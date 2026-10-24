package com.relay.app.event

import com.relay.app.data.event.EventDao
import com.relay.app.data.event.EventEntity
import com.relay.app.data.schedule.ScheduleEntity
import com.relay.app.schedule.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID

/**
 * The command layer for calendar items (spec §10b). An item's reminder is an ordinary schedule,
 * written through [ScheduleRepository] so arming stays in one place. One mutex serialises item
 * writes; the schedule side has its own.
 */
class EventRepository(
    private val dao: EventDao,
    private val schedules: ScheduleRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    fun observeAll(): Flow<List<EventEntity>> = dao.observeAll()

    suspend fun get(id: String): EventEntity? = dao.get(id)

    /** The schedule that reminds of item [id], if it has one. */
    suspend fun reminderFor(id: String): ScheduleEntity? = schedules.forEvent(id).firstOrNull()

    /**
     * Saves [event] and its reminder; a null [reminder] removes it. Returns when the reminder
     * fires next, or null when there is none or its time has passed.
     */
    suspend fun save(event: EventEntity, reminder: ReminderDraft?): Instant? = mutex.withLock {
        dao.upsert(event)
        val existing = schedules.forEvent(event.id)
        if (reminder == null) {
            existing.forEach { schedules.delete(it.id) }
            return null
        }
        // One reminder per item for now; edit it in place so its id stays stable.
        val keep = existing.firstOrNull()
        existing.drop(1).forEach { schedules.delete(it.id) }
        schedules.save(
            EventReminders.scheduleFor(event, reminder, keep?.id ?: UUID.randomUUID().toString(), keep?.createdAtMillis ?: nowMillis()),
        )
    }

    /** Deletes item [id]. Its reminders go first, so a deleted item can never fire. */
    suspend fun delete(id: String) = mutex.withLock {
        schedules.forEvent(id).forEach { schedules.delete(it.id) }
        dao.delete(id)
    }
}
