package com.clockr.app.ui.alarms

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clockr.app.alarm.AlarmScheduler
import com.clockr.app.data.AlarmEntity
import com.clockr.app.data.ClockrDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmsViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ClockrDatabase.get(application).alarmDao()
    private val scheduler = AlarmScheduler(application)

    val alarms: StateFlow<List<AlarmEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(alarm: AlarmEntity, enabled: Boolean) = viewModelScope.launch {
        val updated = alarm.copy(enabled = enabled)
        dao.upsert(updated)
        if (enabled) scheduler.schedule(updated) else scheduler.cancel(updated)
    }

    /** Un-marking "done" reactivates the alarm for its next natural occurrence of that time. */
    fun setDone(alarm: AlarmEntity, done: Boolean) = viewModelScope.launch {
        val updated = alarm.copy(done = done, enabled = !done, snoozesUsed = 0)
        dao.upsert(updated)
        if (updated.enabled) scheduler.schedule(updated) else scheduler.cancel(updated)
    }

    fun delete(alarm: AlarmEntity) = viewModelScope.launch {
        scheduler.cancel(alarm)
        dao.delete(alarm)
    }
}
