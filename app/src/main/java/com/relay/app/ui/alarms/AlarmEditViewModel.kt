package com.relay.app.ui.alarms

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.relay.app.appContainer
import com.relay.app.data.schedule.AfterFire
import com.relay.app.data.schedule.ExceptionKind
import com.relay.app.data.schedule.RingOptions
import com.relay.app.data.schedule.ScheduleEntity
import com.relay.app.data.schedule.ScheduleExceptionEntity
import com.relay.app.data.schedule.TargetKind
import com.relay.app.data.schedule.WeekdayMask
import com.relay.app.schedule.NextFire
import com.relay.app.schedule.TargetSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class EndRule { NEVER, DATE, COUNT }

/** Another alarm, for "apply this exception to these too". */
data class OtherAlarm(val id: String, val hour: Int, val minute: Int, val label: String)

data class AlarmEditState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val id: String = UUID.randomUUID().toString(),
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "",
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val oneOffDate: LocalDate? = null,
    val afterFire: AfterFire = AfterFire.DISABLE,
    val endRule: EndRule = EndRule.NEVER,
    val endDate: LocalDate? = null,
    val endCount: Int = 10,
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val rampSeconds: Int = 0,
    val snoozeMinutes: Int = 10,
    val maxSnoozes: Int? = null,
    val exceptions: List<ScheduleExceptionEntity> = emptyList(),
    val others: List<OtherAlarm> = emptyList(),
    val firstDay: DayOfWeek = DayOfWeek.MONDAY,
    val is24h: Boolean = true,
    /** Preserved from the loaded row, so an edit doesn't reset them. */
    val createdAtMillis: Long = System.currentTimeMillis(),
    val createdBy: String = "user",
)

/** Editor for one alarm. [scheduleId] null = a new one, prefilled from Settings defaults. */
class AlarmEditViewModel(application: Application, private val scheduleId: String?) : AndroidViewModel(application) {
    private val container = application.appContainer
    private val _state = MutableStateFlow(AlarmEditState())
    val state: StateFlow<AlarmEditState> = _state.asStateFlow()
    /** What it fires (spec §04). */
    val fires = TargetEditor(container, viewModelScope)

    init {
        viewModelScope.launch {
            val settings = container.settings.current()
            val existing = scheduleId?.let { container.schedules.get(it) }
            val base = AlarmEditState(
                loaded = true,
                firstDay = settings.firstDayOfWeek,
                is24h = settings.clockFormat24h,
                snoozeMinutes = settings.defaultSnoozeMinutes,
                soundUri = settings.defaultAlarmSoundUri,
                rampSeconds = settings.defaultRampSeconds,
            )
            _state.value = if (existing == null) base else base.copy(
                isNew = false,
                id = existing.id,
                hour = existing.hour,
                minute = existing.minute,
                label = existing.label,
                repeatDays = WeekdayMask.toSet(existing.repeatDays),
                oneOffDate = existing.oneOffEpochDay?.let(LocalDate::ofEpochDay),
                afterFire = existing.afterFire,
                endRule = when {
                    existing.endEpochDay != null -> EndRule.DATE
                    existing.remainingCount != null -> EndRule.COUNT
                    else -> EndRule.NEVER
                },
                endDate = existing.endEpochDay?.let(LocalDate::ofEpochDay),
                endCount = existing.remainingCount ?: 10,
                soundUri = existing.ring.soundUri,
                vibrate = existing.ring.vibrate,
                rampSeconds = existing.ring.rampSeconds,
                snoozeMinutes = existing.ring.snoozeMinutes,
                maxSnoozes = existing.ring.maxSnoozes,
                createdAtMillis = existing.createdAtMillis,
                createdBy = existing.createdBy,
            )
            if (existing == null) fires.load(TargetKind.RING, null, TargetSpec())
            else fires.load(existing.target, existing.targetDefId, TargetSpec.decode(existing.targetSpec))
            baseline = _state.value.editable() + fires.editable()
            refreshExceptions()
        }
    }

    fun update(transform: (AlarmEditState) -> AlarmEditState) = _state.update(transform)

    private var baseline: List<Any?>? = null
    private fun AlarmEditState.editable() = listOf(
        hour, minute, label, repeatDays, oneOffDate, afterFire, endRule, endDate, endCount,
        soundUri, vibrate, rampSeconds, snoozeMinutes, maxSnoozes,
    )

    /** Unsaved edits? Exceptions don't count: they're saved the moment they're added. */
    fun isDirty(): Boolean = baseline != null && baseline != _state.value.editable() + fires.editable()

    /** Saves and arms. [onSaved] gets the next fire time (null = won't ring). */
    fun save(onSaved: (Long?) -> Unit) = viewModelScope.launch {
        val s = _state.value
        val f = fires.state.value
        val oneOff = s.repeatDays.isEmpty()
        val entity = ScheduleEntity(
            id = s.id,
            label = s.label.trim(),
            hour = s.hour,
            minute = s.minute,
            oneOffEpochDay = if (oneOff) (s.oneOffDate ?: NextFire.nextDateFor(s.hour, s.minute, Instant.now(), ZoneId.systemDefault())).toEpochDay() else null,
            repeatDays = WeekdayMask.of(s.repeatDays),
            endEpochDay = if (!oneOff && s.endRule == EndRule.DATE) s.endDate?.toEpochDay() else null,
            remainingCount = if (!oneOff && s.endRule == EndRule.COUNT) s.endCount.coerceAtLeast(1) else null,
            afterFire = s.afterFire,
            // Saving is a clear "I want this alarm": always enable.
            enabled = true,
            target = f.target,
            ring = RingOptions(s.soundUri, s.vibrate, s.rampSeconds, s.snoozeMinutes, s.maxSnoozes),
            createdAtMillis = s.createdAtMillis,
            createdBy = s.createdBy,
            snoozedUntilMillis = null,
            snoozesUsed = 0,
            nextFireAtMillis = null,
            targetDefId = fires.storedDefId(f),
            targetSpec = TargetSpec.encode(f.spec),
        )
        val next = container.schedules.save(entity)
        onSaved(next?.toEpochMilli())
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        container.schedules.delete(_state.value.id)
        onDeleted()
    }

    fun addException(start: LocalDate, end: LocalDate, kind: ExceptionKind, hour: Int?, minute: Int?, alsoFor: Set<String>) =
        viewModelScope.launch {
            container.schedules.addException(setOf(_state.value.id) + alsoFor, start, end, kind, hour, minute)
            refreshExceptions()
        }

    fun deleteException(exception: ScheduleExceptionEntity) = viewModelScope.launch {
        container.schedules.deleteException(exception)
        refreshExceptions()
    }

    private suspend fun refreshExceptions() {
        val id = _state.value.id
        val exceptions = if (_state.value.isNew) emptyList() else container.schedules.exceptionsFor(id)
        val others = container.schedules.all().filter { it.id != id }
            .sortedBy { it.hour * 60 + it.minute }
            .map { OtherAlarm(it.id, it.hour, it.minute, it.label) }
        _state.update { it.copy(exceptions = exceptions, others = others) }
    }

    companion object {
        fun factory(application: Application, scheduleId: String?) = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                AlarmEditViewModel(application, scheduleId) as T
        }
    }
}
