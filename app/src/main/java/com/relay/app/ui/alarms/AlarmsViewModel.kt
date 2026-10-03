package com.relay.app.ui.alarms

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.relay.app.appContainer
import com.relay.app.data.settings.AppSettings
import com.relay.app.schedule.AlarmListItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmsViewModel(application: Application) : AndroidViewModel(application) {
    private val schedules = application.appContainer.schedules

    val alarms: StateFlow<List<AlarmListItem>?> =
        schedules.observeAlarms().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<AppSettings?> =
        application.appContainer.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Saved timer/relay labels by id, to describe what an alarm starts. */
    val blockLabels: StateFlow<Map<String, String>> = application.appContainer.library.observe()
        .map { state -> state.items.associate { it.id to it.label } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setEnabled(id: String, enabled: Boolean, onArmed: (Long?) -> Unit) = viewModelScope.launch {
        val next = schedules.setEnabled(id, enabled)
        if (enabled) onArmed(next?.toEpochMilli())
    }
}
