package com.relay.app.ui.stopwatch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.relay.app.appContainer
import com.relay.app.stopwatch.EditError
import com.relay.app.stopwatch.StopwatchView
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StopwatchViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = application.appContainer.stopwatches

    val stopwatches: StateFlow<List<StopwatchView>?> = repo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String, t0Millis: Long, tags: List<String>) = viewModelScope.launch { repo.create(name, t0Millis, tags) }
    fun pause(id: String) = viewModelScope.launch { repo.pause(id) }
    fun resume(id: String) = viewModelScope.launch { repo.resume(id) }
    fun lap(id: String, tags: List<String> = emptyList()) = viewModelScope.launch { repo.lap(id, tags) }
    fun restart(id: String) = viewModelScope.launch { repo.restart(id) }
    fun updateDetails(id: String, name: String, tags: List<String>) = viewModelScope.launch { repo.updateDetails(id, name, tags) }
    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
    fun setLapDetails(lapId: String, note: String, tags: List<String>) = viewModelScope.launch { repo.setLapDetails(lapId, note, tags) }
    fun undo(id: String) = viewModelScope.launch { repo.undo(id) }
    fun redo(id: String) = viewModelScope.launch { repo.redo(id) }

    /** Runs a correction; [onResult] gets null on success, or why it was refused. */
    fun removeLap(id: String, lapId: String, label: String, onResult: (EditError?) -> Unit) =
        viewModelScope.launch { onResult(repo.removeLap(id, lapId, label)) }
    fun insertLap(id: String, lapId: String, intoMillis: Long, label: String, onResult: (EditError?) -> Unit) =
        viewModelScope.launch { onResult(repo.insertLap(id, lapId, intoMillis, label)) }
    fun adjustLap(id: String, lapId: String, deltaMillis: Long, label: String, onResult: (EditError?) -> Unit) =
        viewModelScope.launch { onResult(repo.adjustLap(id, lapId, deltaMillis, label)) }
}
