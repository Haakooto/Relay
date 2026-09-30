package com.relay.app.ui.timers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.relay.app.appContainer
import com.relay.app.data.library.SoundSpec
import com.relay.app.data.library.TimerDefEntity
import com.relay.app.library.SaveError
import com.relay.app.library.SaveResult
import com.relay.app.run.TimerSpec
import com.relay.chainlang.ChainLang
import com.relay.chainlang.ChainLangException
import com.relay.chainlang.NowClock
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
    val durationExpr: String = "5m",
    val sound: SoundSpec = SoundSpec(),
    val headerId: String? = null,
    val sortOrder: Long = System.currentTimeMillis(),
    val seconds: Long? = 300,
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
            )
            reevaluate()
        }
    }

    fun setLabel(v: String) { _state.update { it.copy(label = v, saveError = null) } }
    fun setDuration(v: String) { _state.update { it.copy(durationExpr = v) }; reevaluate() }
    fun setSound(v: SoundSpec) { _state.update { it.copy(sound = v) } }

    private fun reevaluate() {
        val expr = _state.value.durationExpr
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
        library.startTimerSpec(TimerSpec(s.label.trim(), s.durationExpr, s.sound), sourceId = if (s.isNew) null else s.id)
        onStarted()
    }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val s = _state.value
        val result = library.saveTimer(TimerDefEntity(s.id, s.label.trim(), s.durationExpr.trim(), s.sound, s.headerId, s.sortOrder))
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
