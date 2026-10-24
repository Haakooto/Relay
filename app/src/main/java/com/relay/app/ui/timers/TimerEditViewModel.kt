package com.relay.app.ui.timers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.relay.app.appContainer
import com.relay.app.data.library.SoundSpec
import com.relay.app.data.library.TimerDefEntity
import com.relay.app.data.library.TransitionTone
import com.relay.app.library.SaveError
import com.relay.app.library.SaveResult
import com.relay.app.run.TimerSpec
import com.relay.app.run.TransitionBeep
import com.relay.app.util.DurationText
import com.relay.chainlang.ChainLang
import com.relay.chainlang.ChainLangException
import com.relay.chainlang.NowClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class TimerEditState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    /** Empty for a new timer: the keypad starts from nothing, like the stock clock's. */
    val durationExpr: String = "",
    val sound: SoundSpec = SoundSpec(),
    val warnSeconds: Long = 0,
    val warnTone: TransitionTone = TransitionTone.BEEP,
    val headerId: String? = null,
    val sortOrder: Long = System.currentTimeMillis(),
    val seconds: Long? = null,
    val durationError: String? = null,
    /** The expression parsed but is more than one step, e.g. "5, 10". */
    val notSingle: Boolean = false,
    val saveError: SaveError? = null,
    /** Set while asking to confirm a delete: the relays that would break. */
    val deleteReferences: List<String>? = null,
)

/**
 * Timer editor (spec §05). It edits a draft: the saved timer only changes on Save, so anything
 * started meanwhile (from the library, or by an alarm) uses the saved version.
 */
class TimerEditViewModel(application: Application, private val timerId: String?) : AndroidViewModel(application) {
    private val library = application.appContainer.library
    private val _state = MutableStateFlow(TimerEditState())
    val state: StateFlow<TimerEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = timerId?.let { library.timer(it) }
            _state.value = if (existing == null) TimerEditState(loaded = true) else TimerEditState(
                loaded = true, isNew = false, id = existing.id, label = existing.label, durationExpr = existing.durationExpr,
                sound = existing.sound, headerId = existing.headerId, sortOrder = existing.sortOrder,
                warnSeconds = existing.warnSeconds, warnTone = existing.warnTone,
            )
            baseline = _state.value.editable()
            reevaluate()
        }
    }

    private var baseline: List<Any?>? = null
    private fun TimerEditState.editable() = listOf(label, durationExpr, sound, warnSeconds, warnTone)
    fun isDirty(): Boolean = baseline != null && baseline != _state.value.editable()

    fun setLabel(v: String) { _state.update { it.copy(label = v, saveError = null) } }
    fun setDuration(v: String) { _state.update { it.copy(durationExpr = v) }; reevaluate() }
    fun setSound(v: SoundSpec) { _state.update { it.copy(sound = v) } }
    fun setWarnSeconds(v: Long) { _state.update { it.copy(warnSeconds = v) } }
    fun setWarnTone(v: TransitionTone) {
        _state.update { it.copy(warnTone = v) }
        viewModelScope.launch(Dispatchers.Default) { TransitionBeep.play(v) }
    }

    /** +1m/+5m/+10m: a plain duration is rewritten ("5m" + 1m = "6m"); anything else gets "+ 1m". */
    fun addToDuration(seconds: Long) {
        val s = _state.value
        val expr = s.durationExpr.trim()
        setDuration(
            when {
                expr.isEmpty() -> DurationText.expr(seconds)
                s.seconds != null && DurationText.isPlain(expr) -> DurationText.expr(s.seconds + seconds)
                else -> "$expr + ${DurationText.expr(seconds)}"
            },
        )
    }

    private fun reevaluate() {
        val expr = _state.value.durationExpr
        if (expr.isBlank()) {
            _state.update { it.copy(seconds = null, durationError = null, notSingle = false) }
            return
        }
        val (seconds, error) = try {
            val steps = ChainLang.run("($expr)", NowClock.now())
            if (steps.size != 1) null to null else steps.single().durationSeconds to null
        } catch (e: ChainLangException) {
            null to e.message
        }
        _state.update { it.copy(seconds = seconds, durationError = error, notSingle = seconds == null && error == null) }
    }

    /** Runs the draft as it is now, unsaved. */
    fun start(onStarted: () -> Unit) = viewModelScope.launch {
        val s = _state.value
        if (s.seconds == null) return@launch
        library.startTimerSpec(TimerSpec(s.label.trim(), s.durationExpr, s.sound, s.warnSeconds, s.warnTone), sourceId = if (s.isNew) null else s.id)
        onStarted()
    }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val s = _state.value
        val result = library.saveTimer(TimerDefEntity(s.id, s.label.trim(), s.durationExpr.trim(), s.sound, s.headerId, s.sortOrder, s.warnSeconds, s.warnTone))
        when (result) {
            is SaveResult.Saved -> onSaved()
            is SaveResult.Refused -> _state.update { it.copy(saveError = result.error) }
        }
    }

    fun askDelete() = viewModelScope.launch {
        _state.update { it.copy(deleteReferences = library.referencing(it.label)) }
    }

    fun cancelDelete() = _state.update { it.copy(deleteReferences = null) }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        library.deleteTimer(_state.value.id)
        onDeleted()
    }

    companion object {
        fun factory(application: Application, id: String?) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TimerEditViewModel(application, id) as T
        }
    }
}
