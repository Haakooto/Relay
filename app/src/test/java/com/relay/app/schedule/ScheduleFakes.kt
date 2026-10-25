package com.relay.app.schedule

import com.relay.app.data.schedule.ScheduleDao
import com.relay.app.data.schedule.ScheduleEntity
import com.relay.app.data.schedule.ScheduleExceptionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

// In-memory stand-ins for the schedule repository's Android side, shared by the tests that
// build a real ScheduleRepository.

internal class FakeScheduleDao : ScheduleDao {
    val schedules = MutableStateFlow<Map<String, ScheduleEntity>>(emptyMap())
    val exceptions = MutableStateFlow<Map<String, ScheduleExceptionEntity>>(emptyMap())
    override fun observeAll(): Flow<List<ScheduleEntity>> = schedules.map { it.values.toList() }
    override suspend fun getAll() = schedules.value.values.toList()
    override suspend fun get(id: String) = schedules.value[id]
    override suspend fun forEvent(eventId: String) = schedules.value.values.filter { it.eventId == eventId }
    override suspend fun moveCalendar(from: String, to: String) {
        schedules.value = schedules.value.mapValues { (_, s) -> if (s.calendarId == from) s.copy(calendarId = to) else s }
    }
    override suspend fun upsert(schedule: ScheduleEntity) { schedules.value += schedule.id to schedule }
    override suspend fun delete(id: String) {
        schedules.value -= id
        exceptions.value = exceptions.value.filterValues { it.scheduleId != id }
    }
    override fun observeAllExceptions(): Flow<List<ScheduleExceptionEntity>> = exceptions.map { it.values.toList() }
    override suspend fun exceptionsFor(scheduleId: String) = exceptions.value.values.filter { it.scheduleId == scheduleId }
    override suspend fun upsertException(exception: ScheduleExceptionEntity) { exceptions.value += exception.id to exception }
    override suspend fun deleteException(id: String) { exceptions.value -= id }
    override suspend fun deleteExceptionsEndingBefore(beforeEpochDay: Long) {
        exceptions.value = exceptions.value.filterValues { it.endEpochDay >= beforeEpochDay }
    }
}

internal class FakeScheduleArming : ScheduleArming {
    data class Armed(val triggerAt: Long, val isSnooze: Boolean, val at: Long)
    val armed = mutableMapOf<String, Armed>()
    val upcoming = mutableMapOf<String, Long>()
    var lastRings = true
    override fun arm(scheduleId: String, triggerAtMillis: Long, isSnooze: Boolean, atMillis: Long, rings: Boolean) {
        lastRings = rings
        armed[scheduleId] = Armed(triggerAtMillis, isSnooze, atMillis)
    }
    override fun cancel(scheduleId: String) { armed -= scheduleId }
    override fun armUpcoming(scheduleId: String, atMillis: Long) { upcoming[scheduleId] = atMillis }
    override fun cancelUpcoming(scheduleId: String) { upcoming -= scheduleId }
}

internal class FakeScheduleNotices : ScheduleNotices {
    val shown = mutableListOf<String>()
    override suspend fun showUpcoming(s: ScheduleEntity, fireAt: Instant) { shown += "upcoming:${s.id}" }
    override suspend fun showSnoozed(s: ScheduleEntity, until: Instant) { shown += "snoozed:${s.id}" }
    override suspend fun showMissed(s: ScheduleEntity, at: Instant) { shown += "missed:${s.id}" }
    override fun clearUpcoming(scheduleId: String) { /* nothing posted to clear */ }
    override fun clearScheduleNotifications(scheduleId: String) { /* nothing posted to clear */ }
}
