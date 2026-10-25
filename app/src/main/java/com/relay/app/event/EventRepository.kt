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

/** Copies of items in a phone calendar (#16). Implemented by `PhoneCalendars`; faked in tests. */
interface PhoneCopies {
    /** Writes [event]'s copy into [phoneCalendarId], updating its existing one; its id, or null if it couldn't. */
    suspend fun write(event: EventEntity, phoneCalendarId: Long): Long?
    suspend fun delete(providerEventId: Long)
}

/**
 * The command layer for calendar items (spec §10b). An item's reminder is an ordinary schedule,
 * written through [ScheduleRepository] so arming stays in one place. One mutex serialises item
 * writes; the schedule side has its own. When an item's calendar copies to a phone calendar
 * ([exportTarget], #16), its copy there is kept in step; a copy that can't be written is skipped,
 * never failing the save.
 */
class EventRepository(
    private val dao: EventDao,
    private val schedules: ScheduleRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val copies: PhoneCopies? = null,
    private val exportTarget: suspend (calendarId: String) -> Long? = { null },
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
        // The editor doesn't know about the copy; keep it from the stored row.
        dao.upsert(syncCopy(event.copy(providerEventId = dao.get(event.id)?.providerEventId)))
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

    /** Deletes item [id]. Its reminders go first, so a deleted item can never fire; then its copy. */
    suspend fun delete(id: String) = mutex.withLock {
        schedules.forEvent(id).forEach { schedules.delete(it.id) }
        dao.get(id)?.providerEventId?.let { copies?.delete(it) }
        dao.delete(id)
    }

    /** After a calendar's copy target changed, or it took another's items: brings every copy in step. */
    suspend fun syncCalendar(calendarId: String) = mutex.withLock {
        dao.inCalendar(calendarId).forEach { dao.upsert(syncCopy(it)) }
    }

    /** [event] with its copy written to its calendar's phone calendar, or removed if there's none now. */
    private suspend fun syncCopy(event: EventEntity): EventEntity {
        val copies = copies ?: return event
        val target = exportTarget(event.calendarId)
        if (target == null) {
            event.providerEventId?.let { copies.delete(it) }
            return event.copy(providerEventId = null)
        }
        return event.copy(providerEventId = copies.write(event, target) ?: event.providerEventId)
    }
}
