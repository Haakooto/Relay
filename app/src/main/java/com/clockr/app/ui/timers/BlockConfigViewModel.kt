package com.clockr.app.ui.timers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clockr.app.data.BlockRepository
import com.clockr.app.data.ChainDefEntity
import com.clockr.app.data.ClockrDatabase
import com.clockr.app.data.SettingsRepository
import com.clockr.app.data.TimerDefEntity
import com.clockr.app.domain.BlockKind
import com.clockr.app.domain.TransitionMode
import com.clockr.app.run.TimerRunService
import com.clockr.chainlang.ChainLang
import com.clockr.chainlang.ChainLangException
import com.clockr.chainlang.SafetyLimits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Sentinel blockIds the "+" menu on the Timers pane navigates to — recognized below instead of
 *  a real Room lookup, so a fresh block and an existing one share the exact same load/edit/save
 *  path once the blank state is populated. */
const val NEW_TIMER_ROUTE_ID = "new_timer"
const val NEW_CHAIN_ROUTE_ID = "new_chain"

/** Chain-syntax keywords (spec §6.4) — a label can't collide with these or every reference to it breaks. */
private val RESERVED_WORDS = setOf("repeat", "range", "arange", "MINUTE", "HOUR", "SECOND", "RANDOM", "NOW")

/** A label doubles as a chainlang IDENT (spec §6.4), so it has to be a single valid identifier —
 *  spaces become underscores rather than being silently dropped, matching what people expect. */
private fun sanitizeLabel(raw: String): String =
    raw.replace(Regex("\\s+"), "_").filter { it.isLetterOrDigit() || it == '_' }

data class ResolvedStepUi(val label: String?, val seconds: Long)

data class BlockConfigUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val isNew: Boolean = true,
    val id: String = "",
    val kind: BlockKind = BlockKind.TIMER,
    val label: String = "",
    val labelError: String? = null,
    val persistent: Boolean = false,
    val soundProfileId: String = "",
    // Timer-only
    val durationExpr: String = "",
    val resolvedSeconds: Long? = null,
    // Chain-only
    val contentExpr: String = "",
    val transitionMode: TransitionMode = TransitionMode.SEAMLESS,
    val resolvedSteps: List<ResolvedStepUi> = emptyList(),
    val error: String? = null,
    val saved: Boolean = false,
    /** Non-null while a delete is pending confirmation — the labels of chains that reference this block. */
    val pendingDeleteUsedBy: List<String>? = null,
)

class BlockConfigViewModel(application: Application, private val blockId: String) : AndroidViewModel(application) {
    private val db = ClockrDatabase.get(application)
    private val repository = BlockRepository(db)
    private val settingsRepository = SettingsRepository(application)

    private suspend fun currentLimits(): SafetyLimits =
        SafetyLimits(maxPlanSize = settingsRepository.current().maxExecutionPlanSize)

    private val _uiState = MutableStateFlow(BlockConfigUiState())
    val uiState: StateFlow<BlockConfigUiState> = _uiState

    init {
        viewModelScope.launch {
            if (blockId == NEW_TIMER_ROUTE_ID) {
                _uiState.value = BlockConfigUiState(
                    loading = false,
                    id = UUID.randomUUID().toString(),
                    kind = BlockKind.TIMER,
                    label = "",
                    persistent = false,
                    soundProfileId = "",
                    durationExpr = "1m",
                )
                revalidateTimer()
                return@launch
            }
            if (blockId == NEW_CHAIN_ROUTE_ID) {
                _uiState.value = BlockConfigUiState(
                    loading = false,
                    id = UUID.randomUUID().toString(),
                    kind = BlockKind.CHAIN,
                    label = "",
                    persistent = false,
                    soundProfileId = "",
                    contentExpr = "()",
                    transitionMode = TransitionMode.SEAMLESS,
                )
                revalidateChain()
                return@launch
            }
            db.timerDefDao().findById(blockId)?.let { timer ->
                _uiState.value = BlockConfigUiState(
                    loading = false,
                    isNew = false,
                    id = timer.id,
                    kind = BlockKind.TIMER,
                    label = timer.label,
                    persistent = timer.persistent,
                    soundProfileId = timer.soundProfileId,
                    durationExpr = timer.durationExpr,
                )
                revalidateTimer()
                return@launch
            }
            db.chainDefDao().findById(blockId)?.let { chain ->
                _uiState.value = BlockConfigUiState(
                    loading = false,
                    isNew = false,
                    id = chain.id,
                    kind = BlockKind.CHAIN,
                    label = chain.label,
                    persistent = chain.persistent,
                    soundProfileId = chain.defaultSoundProfileId,
                    contentExpr = chain.contentExpr,
                    transitionMode = TransitionMode.valueOf(chain.transitionMode),
                )
                revalidateChain()
                return@launch
            }
            _uiState.value = BlockConfigUiState(loading = false, notFound = true)
        }
    }

    /** Giving something a name is a pretty clear signal you want to keep it. */
    fun updateLabel(value: String) {
        val sanitized = sanitizeLabel(value)
        val current = _uiState.value
        val autoPersistent = current.persistent || sanitized.isNotBlank()
        _uiState.value = current.copy(label = sanitized, persistent = autoPersistent)
        revalidateLabel()
    }
    fun updatePersistent(value: Boolean) {
        _uiState.value = _uiState.value.copy(persistent = value)
        revalidateLabel()
    }
    fun updateSoundProfileId(value: String) { _uiState.value = _uiState.value.copy(soundProfileId = value) }
    fun updateTransitionMode(value: TransitionMode) { _uiState.value = _uiState.value.copy(transitionMode = value) }

    fun updateDurationExpr(value: String) {
        _uiState.value = _uiState.value.copy(durationExpr = value)
        revalidateTimer()
    }

    fun updateContentExpr(value: String) {
        _uiState.value = _uiState.value.copy(contentExpr = value)
        revalidateChain()
    }

    private fun revalidateLabel() = viewModelScope.launch {
        val state = _uiState.value
        _uiState.value = state.copy(labelError = validateLabel(state.label, state.persistent))
    }

    /** Syntax only, once we already know a label is required — cheap, safe on every keystroke. */
    private fun validateLabelSyntax(label: String): String? = when {
        label.isBlank() -> "Persistent blocks need a label"
        !label.first().isLetter() -> "Must start with a letter"
        label in RESERVED_WORDS -> "\"$label\" is reserved by the chain syntax"
        else -> null
    }

    /**
     * A label is only required — and only checked for validity/uniqueness — once a block is
     * Persistent: an anonymous, ephemeral Timer/Chain doesn't need one (it's never referenced by
     * label, and never shows in the library). Toggling Persistent on with a blank label surfaces
     * this immediately rather than waiting for Save.
     */
    private suspend fun validateLabel(label: String, persistent: Boolean): String? {
        if (!persistent) return null
        validateLabelSyntax(label)?.let { return it }
        val id = _uiState.value.id
        // A non-persistent row is invisible and disposable — it should never permanently squat
        // on a label. Only persistent rows count as "already in use".
        val timerHit = db.timerDefDao().findByLabel(label)?.takeIf { it.id != id && it.persistent }
        val chainHit = db.chainDefDao().findByLabel(label)?.takeIf { it.id != id && it.persistent }
        return if (timerHit != null || chainHit != null) "\"$label\" is already in use" else null
    }

    private fun revalidateTimer() = viewModelScope.launch {
        val expr = _uiState.value.durationExpr
        try {
            val seconds = repository.evalDurationPreview(expr)
            _uiState.value = _uiState.value.copy(resolvedSeconds = seconds, error = null)
        } catch (e: ChainLangException) {
            _uiState.value = _uiState.value.copy(resolvedSeconds = null, error = e.message)
        }
    }

    private fun revalidateChain() = viewModelScope.launch {
        val expr = _uiState.value.contentExpr
        try {
            val steps = repository.previewChainSteps(expr, currentLimits())
            _uiState.value = _uiState.value.copy(
                resolvedSteps = steps.map { ResolvedStepUi(it.first, it.second) },
                error = null,
            )
        } catch (e: ChainLangException) {
            _uiState.value = _uiState.value.copy(resolvedSteps = emptyList(), error = e.message)
        }
    }

    fun save(onSuccess: () -> Unit) = viewModelScope.launch {
        val state = _uiState.value
        val labelIssue = validateLabel(state.label, state.persistent)
        if (labelIssue != null) {
            _uiState.value = state.copy(labelError = labelIssue)
            return@launch
        }
        try {
            when (state.kind) {
                BlockKind.TIMER -> repository.saveTimer(
                    TimerDefEntity(
                        id = state.id,
                        label = state.label,
                        durationExpr = state.durationExpr,
                        soundProfileId = state.soundProfileId,
                        persistent = state.persistent,
                    ),
                )
                BlockKind.CHAIN -> repository.saveChain(
                    ChainDefEntity(
                        id = state.id,
                        label = state.label,
                        contentExpr = state.contentExpr,
                        defaultSoundProfileId = state.soundProfileId,
                        transitionMode = state.transitionMode.name,
                        persistent = state.persistent,
                    ),
                    currentLimits(),
                )
            }
            _uiState.value = state.copy(error = null, saved = true)
            // Spec §6.2: creating a Timer/Chain always starts it immediately — Persistent only
            // decides whether the definition survives afterward. Editing an existing one just
            // saves; it doesn't restart whatever might currently be running.
            if (state.isNew) {
                TimerRunService.start(getApplication(), state.id, state.kind)
            }
            onSuccess()
        } catch (e: ChainLangException) {
            _uiState.value = state.copy(error = e.message)
        }
    }

    /** Which persistent chains (other than this one) reference this block's label, if any. */
    private suspend fun usedBy(label: String, ownId: String): List<String> =
        db.chainDefDao().getAll()
            .filter { it.id != ownId }
            .filter { label in ChainLang.referencedLabels(it.contentExpr) }
            .map { it.label }

    /** Deletes immediately if nothing references this block; otherwise asks for confirmation first. */
    fun requestDelete(onSuccess: () -> Unit) = viewModelScope.launch {
        val state = _uiState.value
        val referencedBy = if (state.persistent) usedBy(state.label, state.id) else emptyList()
        if (referencedBy.isEmpty()) {
            performDelete(state)
            onSuccess()
        } else {
            _uiState.value = state.copy(pendingDeleteUsedBy = referencedBy)
        }
    }

    fun cancelPendingDelete() {
        _uiState.value = _uiState.value.copy(pendingDeleteUsedBy = null)
    }

    fun confirmDelete(onSuccess: () -> Unit) = viewModelScope.launch {
        performDelete(_uiState.value)
        onSuccess()
    }

    private suspend fun performDelete(state: BlockConfigUiState) {
        when (state.kind) {
            BlockKind.TIMER -> db.timerDefDao().findById(state.id)?.let { db.timerDefDao().delete(it) }
            BlockKind.CHAIN -> db.chainDefDao().findById(state.id)?.let { db.chainDefDao().delete(it) }
        }
    }
}
