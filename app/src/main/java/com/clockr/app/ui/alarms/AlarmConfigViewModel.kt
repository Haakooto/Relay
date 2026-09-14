package com.clockr.app.ui.alarms

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clockr.app.R
import com.clockr.app.alarm.AlarmScheduler
import com.clockr.app.data.AlarmEntity
import com.clockr.app.data.ClockrDatabase
import com.clockr.app.data.OneOffAfterFire
import com.clockr.app.data.SettingsRepository
import com.clockr.app.domain.BlockKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.util.UUID

const val NEW_ALARM_ROUTE_ID = "new_alarm"

enum class DeleteAfterMode { NONE, COUNT, DATE }

/** One persistent Timer/Chain this alarm could target instead of firing itself — spec §4.2. */
data class AlarmTargetOption(val id: String, val label: String, val kind: BlockKind)

data class AlarmConfigUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val isNew: Boolean = true,
    val id: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "",
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val oneOffAfterFire: OneOffAfterFire = OneOffAfterFire.DELETE,
    val done: Boolean = false,
    val deleteAfterMode: DeleteAfterMode = DeleteAfterMode.NONE,
    val repeatCountText: String = "",
    val repeatUntilEpochDay: Long? = null,
    val soundProfileId: String = "",
    val snoozeMinutesText: String = "9",
    val maxSnoozesText: String = "",
    val error: String? = null,
    /** Display-only, sourced from Settings — not persisted on the alarm itself. */
    val is24Hour: Boolean = true,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    /** True picks "Timer or Chain" in the Fires row — independent of whether one's actually been
     *  picked yet, so switching into that mode starts unset ("Select a Timer or Chain") instead
     *  of silently defaulting to whichever one happened to be first in the list. */
    val firesBlockTarget: Boolean = false,
    val targetBlockId: String? = null,
    val targetBlockKind: BlockKind? = null,
    val availableTargets: List<AlarmTargetOption> = emptyList(),
) {
    val isRepeating: Boolean get() = repeatDays.isNotEmpty()
    val hasBlockTarget: Boolean get() = targetBlockId != null && targetBlockKind != null
}

class AlarmConfigViewModel(application: Application, private val alarmId: String) : AndroidViewModel(application) {
    private val db = ClockrDatabase.get(application)
    private val dao = db.alarmDao()
    private val scheduler = AlarmScheduler(application)
    private val settingsRepository = SettingsRepository(application)

    private val _uiState = MutableStateFlow(AlarmConfigUiState())
    val uiState: StateFlow<AlarmConfigUiState> = _uiState

    init {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            val targets = loadAvailableTargets()
            if (alarmId == NEW_ALARM_ROUTE_ID) {
                _uiState.value = AlarmConfigUiState(
                    loading = false,
                    isNew = true,
                    id = UUID.randomUUID().toString(),
                    snoozeMinutesText = settings.defaultSnoozeMinutes.toString(),
                    is24Hour = settings.clockFormat24h,
                    firstDayOfWeek = settings.firstDayOfWeek,
                    availableTargets = targets,
                )
                return@launch
            }
            val alarm = dao.findById(alarmId)
            if (alarm == null) {
                _uiState.value = AlarmConfigUiState(
                    loading = false,
                    notFound = true,
                    is24Hour = settings.clockFormat24h,
                    firstDayOfWeek = settings.firstDayOfWeek,
                    availableTargets = targets,
                )
                return@launch
            }
            _uiState.value = AlarmConfigUiState(
                loading = false,
                isNew = false,
                id = alarm.id,
                hour = alarm.hour,
                minute = alarm.minute,
                label = alarm.label,
                repeatDays = alarm.repeatDaySet(),
                oneOffAfterFire = alarm.oneOffAfterFire,
                done = alarm.done,
                deleteAfterMode = when {
                    alarm.repeatOccurrencesRemaining != null -> DeleteAfterMode.COUNT
                    alarm.repeatUntilEpochDay != null -> DeleteAfterMode.DATE
                    else -> DeleteAfterMode.NONE
                },
                repeatCountText = alarm.repeatOccurrencesRemaining?.toString() ?: "",
                repeatUntilEpochDay = alarm.repeatUntilEpochDay,
                soundProfileId = alarm.soundProfileId,
                snoozeMinutesText = alarm.snoozeMinutes.toString(),
                maxSnoozesText = alarm.maxSnoozes?.toString() ?: "",
                is24Hour = settings.clockFormat24h,
                firstDayOfWeek = settings.firstDayOfWeek,
                firesBlockTarget = alarm.hasBlockTarget,
                targetBlockId = alarm.targetBlockId,
                targetBlockKind = alarm.targetBlockKind?.let { runCatching { BlockKind.valueOf(it) }.getOrNull() },
                availableTargets = targets,
            )
        }
    }

    /** Only persistent blocks are referenceable/targetable — same rule as chain references. */
    private suspend fun loadAvailableTargets(): List<AlarmTargetOption> {
        val timers = db.timerDefDao().observeAll().first().map { AlarmTargetOption(it.id, it.label, BlockKind.TIMER) }
        val chains = db.chainDefDao().observeAll().first().map { AlarmTargetOption(it.id, it.label, BlockKind.CHAIN) }
        return (timers + chains).sortedBy { it.label.lowercase() }
    }

    fun updateTime(hour: Int, minute: Int) { _uiState.value = _uiState.value.copy(hour = hour, minute = minute) }
    fun updateLabel(value: String) { _uiState.value = _uiState.value.copy(label = value) }

    fun toggleDay(day: DayOfWeek) {
        val current = _uiState.value.repeatDays
        _uiState.value = _uiState.value.copy(repeatDays = if (day in current) current - day else current + day)
    }

    fun updateOneOffAfterFire(value: OneOffAfterFire) { _uiState.value = _uiState.value.copy(oneOffAfterFire = value) }
    fun updateDeleteAfterMode(value: DeleteAfterMode) { _uiState.value = _uiState.value.copy(deleteAfterMode = value) }
    fun updateRepeatCountText(value: String) { _uiState.value = _uiState.value.copy(repeatCountText = value) }
    fun updateRepeatUntilEpochDay(value: Long?) { _uiState.value = _uiState.value.copy(repeatUntilEpochDay = value) }
    fun updateSoundProfileId(value: String) { _uiState.value = _uiState.value.copy(soundProfileId = value) }
    fun updateSnoozeMinutesText(value: String) { _uiState.value = _uiState.value.copy(snoozeMinutesText = value) }
    fun updateMaxSnoozesText(value: String) { _uiState.value = _uiState.value.copy(maxSnoozesText = value) }

    fun clearTarget() {
        _uiState.value = _uiState.value.copy(firesBlockTarget = false, targetBlockId = null, targetBlockKind = null)
    }
    /** Switches into "Timer or Chain" mode without picking one yet — the dropdown starts unset. */
    fun enableBlockTarget() { _uiState.value = _uiState.value.copy(firesBlockTarget = true) }
    fun selectTarget(option: AlarmTargetOption) {
        _uiState.value = _uiState.value.copy(firesBlockTarget = true, targetBlockId = option.id, targetBlockKind = option.kind)
    }

    fun save(onSuccess: () -> Unit) = viewModelScope.launch {
        val s = _uiState.value
        if (s.firesBlockTarget && !s.hasBlockTarget) {
            _uiState.value = s.copy(error = getApplication<Application>().getString(R.string.error_alarm_no_target))
            return@launch
        }
        val entity = AlarmEntity(
            id = s.id,
            hour = s.hour,
            minute = s.minute,
            label = s.label,
            repeatDays = AlarmEntity.encodeDays(s.repeatDays),
            oneOffAfterFire = s.oneOffAfterFire,
            done = s.done,
            repeatUntilEpochDay = if (s.deleteAfterMode == DeleteAfterMode.DATE) s.repeatUntilEpochDay else null,
            repeatOccurrencesRemaining = if (s.deleteAfterMode == DeleteAfterMode.COUNT) s.repeatCountText.toIntOrNull() else null,
            soundProfileId = s.soundProfileId,
            snoozeMinutes = s.snoozeMinutesText.toIntOrNull() ?: settingsRepository.current().defaultSnoozeMinutes,
            maxSnoozes = s.maxSnoozesText.toIntOrNull(),
            snoozesUsed = 0,
            enabled = true,
            targetBlockId = s.targetBlockId,
            targetBlockKind = s.targetBlockKind?.name,
        )
        try {
            dao.upsert(entity)
            scheduler.schedule(entity)
            onSuccess()
        } catch (e: Exception) {
            // The row may already be saved even if scheduling below it fails — surfacing the
            // error (rather than letting it crash) is what stops "still there after relaunch,
            // but never actually armed" from looking like data loss.
            Log.e("AlarmConfigViewModel", "Failed to save/schedule alarm ${entity.id}", e)
            _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.error_alarm_save, e.message ?: e::class.simpleName ?: "unknown"))
        }
    }

    fun delete(onSuccess: () -> Unit) = viewModelScope.launch {
        try {
            dao.findById(_uiState.value.id)?.let { scheduler.cancel(it); dao.delete(it) }
            onSuccess()
        } catch (e: Exception) {
            Log.e("AlarmConfigViewModel", "Failed to delete alarm ${_uiState.value.id}", e)
            _uiState.value = _uiState.value.copy(error = getApplication<Application>().getString(R.string.error_alarm_delete, e.message ?: e::class.simpleName ?: "unknown"))
        }
    }
}
