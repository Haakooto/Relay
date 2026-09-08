package com.clockr.app.ui.timers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clockr.app.data.BlockRepository
import com.clockr.app.data.ChainDefEntity
import com.clockr.app.data.ClockrDatabase
import com.clockr.app.data.TimerDefEntity
import com.clockr.app.domain.BlockKind
import com.clockr.app.domain.TransitionMode
import com.clockr.chainlang.ChainLangException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Sentinel blockIds the "+" menu on the Timers pane navigates to — recognized below instead of
 *  a real Room lookup, so a fresh block and an existing one share the exact same load/edit/save
 *  path once the blank state is populated. */
const val NEW_TIMER_ROUTE_ID = "new_timer"
const val NEW_CHAIN_ROUTE_ID = "new_chain"

data class ResolvedStepUi(val label: String?, val seconds: Long)

data class BlockConfigUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val id: String = "",
    val kind: BlockKind = BlockKind.TIMER,
    val label: String = "",
    val persistent: Boolean = true,
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
)

class BlockConfigViewModel(application: Application, private val blockId: String) : AndroidViewModel(application) {
    private val db = ClockrDatabase.get(application)
    private val repository = BlockRepository(db)

    private val _uiState = MutableStateFlow(BlockConfigUiState())
    val uiState: StateFlow<BlockConfigUiState> = _uiState

    init {
        viewModelScope.launch {
            if (blockId == NEW_TIMER_ROUTE_ID) {
                _uiState.value = BlockConfigUiState(
                    loading = false,
                    id = UUID.randomUUID().toString(),
                    kind = BlockKind.TIMER,
                    label = "New Timer",
                    persistent = true,
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
                    label = "New Chain",
                    persistent = true,
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

    fun updateLabel(value: String) { _uiState.value = _uiState.value.copy(label = value) }
    fun updatePersistent(value: Boolean) { _uiState.value = _uiState.value.copy(persistent = value) }
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
            val steps = repository.previewChainSteps(expr)
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
                )
            }
            _uiState.value = state.copy(error = null, saved = true)
            onSuccess()
        } catch (e: ChainLangException) {
            _uiState.value = state.copy(error = e.message)
        }
    }
}
