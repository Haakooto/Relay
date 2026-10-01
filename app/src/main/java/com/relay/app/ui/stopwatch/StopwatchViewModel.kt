package com.relay.app.ui.stopwatch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.relay.app.appContainer
import com.relay.app.stopwatch.StopwatchView
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StopwatchViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = application.appContainer.stopwatches

    val stopwatches: StateFlow<List<StopwatchView>?> = repo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String, t0Millis: Long) = viewModelScope.launch { repo.create(name, t0Millis) }
    fun pause(id: String) = viewModelScope.launch { repo.pause(id) }
    fun resume(id: String) = viewModelScope.launch { repo.resume(id) }
    fun lap(id: String) = viewModelScope.launch { repo.lap(id) }
    fun restart(id: String) = viewModelScope.launch { repo.restart(id) }
    fun rename(id: String, name: String) = viewModelScope.launch { repo.rename(id, name) }
    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
    fun setLapNote(lapId: String, note: String) = viewModelScope.launch { repo.setLapNote(lapId, note) }
}
