package com.relay.app.ui.timers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.relay.app.appContainer
import com.relay.app.data.library.RelayDefEntity
import com.relay.app.data.library.SoundSpec
import com.relay.app.data.library.StepOverride
import com.relay.app.data.library.TransitionMode
import com.relay.app.data.library.TransitionTone
import com.relay.app.library.LibraryRepository
import com.relay.app.library.SaveError
import com.relay.app.library.SaveResult
import com.relay.app.run.Library
import com.relay.app.run.OwnStep
import com.relay.app.run.PlanBuilder
import com.relay.app.run.RelaySpec
import com.relay.app.run.StepOverrides
import com.relay.app.run.TransitionBeep
import com.relay.chainlang.ChainLangException
import com.relay.chainlang.ChainSyntaxException
import com.relay.chainlang.CycleException
import com.relay.chainlang.NowClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** The live preview under the script (spec §05). */
data class RelayPreview(
    val steps: List<OwnStep> = emptyList(),
    val totalSeconds: Long? = null,
    val planSize: Int = 0,
    val error: String? = null,
    /** Character offset of a syntax error, or -1. */
    val errorPosition: Int = -1,
)

data class RelayEditState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val script: String = "(\n  25m,\n  5m\n)",
    val defaultSound: SoundSpec = SoundSpec(),
    val mode: TransitionMode = TransitionMode.SEAMLESS,
    val tone: TransitionTone = TransitionTone.BEEP,
    val overrides: Map<Int, StepOverride> = emptyMap(),
    val headerId: String? = null,
    val sortOrder: Long = System.currentTimeMillis(),
    val preview: RelayPreview = RelayPreview(),
    /** Names autocomplete may offer: excludes itself and anything that would close a cycle. */
    val candidates: List<String> = emptyList(),
    val longWarningHours: Int = 24,
    val saveError: SaveError? = null,
    val deleteReferences: List<String>? = null,
)

/** Relay editor (spec §05/§06). Edits a draft, like the timer editor. */
class RelayEditViewModel(application: Application, private val relayId: String?) : AndroidViewModel(application) {
    private val container = application.appContainer
    private val library = container.library
    private val _state = MutableStateFlow(RelayEditState())
    val state: StateFlow<RelayEditState> = _state.asStateFlow()
    private var snapshot: Library? = null
    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            val existing = relayId?.let { library.relay(it) }
            val settings = container.settings.current()
            _state.value = (if (existing == null) RelayEditState(loaded = true) else RelayEditState(
                loaded = true, isNew = false, id = existing.id, label = existing.label, script = existing.script,
                defaultSound = existing.defaultSound, mode = existing.transitionMode, tone = existing.transitionTone,
                overrides = StepOverrides.decode(existing.stepOverrides), headerId = existing.headerId, sortOrder = existing.sortOrder,
            )).copy(longWarningHours = settings.longRelayWarningHours)
            snapshot = library.snapshot()
            _state.update { it.copy(candidates = library.referenceCandidates(existing?.label.orEmpty())) }
            schedulePreview(immediately = true)
        }
    }

    fun setLabel(v: String) { _state.update { it.copy(label = v, saveError = null) }; schedulePreview() }
    fun setScript(v: String) {
        if (v == _state.value.script) return
        _state.update { it.copy(script = v, saveError = null) }
        schedulePreview()
    }
    fun setDefaultSound(v: SoundSpec) { _state.update { it.copy(defaultSound = v) }; schedulePreview() }
    fun setMode(v: TransitionMode) { _state.update { it.copy(mode = v) } }
    fun setTone(v: TransitionTone) {
        _state.update { it.copy(tone = v) }
        viewModelScope.launch(Dispatchers.Default) { TransitionBeep.play(v) }
    }
    fun setOverride(index: Int, override: StepOverride?) {
        _state.update { s -> s.copy(overrides = if (override == null) s.overrides - index else s.overrides + (index to override)) }
        schedulePreview()
    }

    private fun spec(s: RelayEditState) = RelaySpec(
        s.label.trim().ifBlank { DRAFT_LABEL }, s.script, s.defaultSound, s.mode, s.tone, s.overrides,
    )

    private fun schedulePreview(immediately: Boolean = false) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            if (!immediately) delay(250)
            val s = _state.value
            val lib = snapshot ?: return@launch
            val settings = container.settings.current()
            val preview = withContext(Dispatchers.Default) {
                try {
                    val builder = PlanBuilder(lib, NowClock.now(), LibraryRepository.limitsFor(settings), SoundSpec(settings.defaultTimerSoundUri))
                    val plan = builder.forRelay(spec(s))
                    RelayPreview(builder.ownSteps(spec(s)), plan.sumOf { it.seconds }, plan.size)
                } catch (e: ChainSyntaxException) {
                    RelayPreview(error = e.message, errorPosition = e.position)
                } catch (e: CycleException) {
                    RelayPreview(error = e.message)
                } catch (e: ChainLangException) {
                    RelayPreview(error = e.message)
                }
            }
            _state.update { it.copy(preview = preview) }
        }
    }

    fun start(onStarted: () -> Unit) = viewModelScope.launch {
        val s = _state.value
        if (s.preview.error != null) return@launch
        try {
            library.startRelaySpec(spec(s).copy(label = s.label.trim()), sourceId = if (s.isNew) null else s.id)
            onStarted()
        } catch (e: ChainLangException) {
            _state.update { it.copy(preview = it.preview.copy(error = e.message)) }
        }
    }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val s = _state.value
        val entity = RelayDefEntity(
            s.id, s.label.trim(), s.script, s.defaultSound, s.mode, s.tone,
            // Overrides past the script's current step count are stale: drop them on save.
            StepOverrides.encode(if (s.preview.steps.isEmpty()) s.overrides else s.overrides.filterKeys { it < s.preview.steps.size }),
            s.headerId, s.sortOrder,
        )
        when (val result = library.saveRelay(entity)) {
            is SaveResult.Saved -> onSaved()
            is SaveResult.Refused -> _state.update { it.copy(saveError = result.error) }
        }
    }

    fun askDelete() = viewModelScope.launch { _state.update { it.copy(deleteReferences = library.referencing(it.label)) } }
    fun cancelDelete() = _state.update { it.copy(deleteReferences = null) }
    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        library.deleteRelay(_state.value.id)
        onDeleted()
    }

    companion object {
        /** Stands in for an unnamed draft in cycle paths; can't collide with a real label. */
        private const val DRAFT_LABEL = "(this relay)"

        fun factory(application: Application, id: String?) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = RelayEditViewModel(application, id) as T
        }
    }
}
