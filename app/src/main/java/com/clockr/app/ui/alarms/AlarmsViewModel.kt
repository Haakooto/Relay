package com.clockr.app.ui.alarms

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clockr.app.alarm.AlarmScheduler
import com.clockr.app.data.AlarmEntity
import com.clockr.app.data.ClockrDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "AlarmsViewModel"

class AlarmsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ClockrDatabase.get(application)
    private val dao = db.alarmDao()
    private val scheduler = AlarmScheduler(application)

    val alarms: StateFlow<List<AlarmEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** blockId -> label, for alarms that fire by starting a Timer/Chain instead of themselves. */
    val targetLabels: StateFlow<Map<String, String>> = combine(
        db.timerDefDao().observeAll(),
        db.chainDefDao().observeAll(),
    ) { timers, chains -> (timers.associate { it.id to it.label } + chains.associate { it.id to it.label }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun setEnabled(alarm: AlarmEntity, enabled: Boolean) = viewModelScope.launch {
        runCatching {
            val updated = alarm.copy(enabled = enabled)
            dao.upsert(updated)
            if (enabled) scheduler.schedule(updated) else scheduler.cancel(updated)
        }.onFailure {
            Log.e(TAG, "Failed to toggle alarm ${alarm.id}", it)
            _error.value = "Couldn't update alarm: ${it.message ?: it::class.simpleName}"
        }
    }

    /** Un-marking "done" reactivates the alarm for its next natural occurrence of that time. */
    fun setDone(alarm: AlarmEntity, done: Boolean) = viewModelScope.launch {
        runCatching {
            val updated = alarm.copy(done = done, enabled = !done, snoozesUsed = 0)
            dao.upsert(updated)
            if (updated.enabled) scheduler.schedule(updated) else scheduler.cancel(updated)
        }.onFailure {
            Log.e(TAG, "Failed to toggle done for alarm ${alarm.id}", it)
            _error.value = "Couldn't update alarm: ${it.message ?: it::class.simpleName}"
        }
    }

    fun delete(alarm: AlarmEntity) = viewModelScope.launch {
        runCatching {
            scheduler.cancel(alarm)
            dao.delete(alarm)
        }.onFailure {
            Log.e(TAG, "Failed to delete alarm ${alarm.id}", it)
            _error.value = "Couldn't delete alarm: ${it.message ?: it::class.simpleName}"
        }
    }

    fun clearError() { _error.value = null }
}
