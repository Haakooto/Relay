package com.relay.app.event

import com.relay.app.data.event.CalendarDao
import com.relay.app.data.event.CalendarEntity
import com.relay.app.data.event.EventDao
import com.relay.app.schedule.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The command layer for calendars (#34). Exactly one calendar is the default for alarms, and
 * there is always at least one: deleting moves the calendar's items and alarms elsewhere first.
 */
class CalendarRepository(
    private val dao: CalendarDao,
    private val events: EventDao,
    private val schedules: ScheduleRepository,
) {
    private val mutex = Mutex()

    fun observeAll(): Flow<List<CalendarEntity>> = dao.observeAll()

    suspend fun all(): List<CalendarEntity> = dao.getAll()

    suspend fun get(id: String): CalendarEntity? = dao.get(id)

    /** Where new alarms go: the calendar marked for it, or the first if somehow none is. */
    suspend fun defaultForAlarms(): CalendarEntity? {
        val all = dao.getAll()
        return all.firstOrNull { it.defaultForAlarms } ?: all.firstOrNull()
    }

    /** Saves [calendar]. Marking it the default for alarms takes that from the others. */
    suspend fun save(calendar: CalendarEntity) = mutex.withLock {
        val all = dao.getAll()
        val sortOrder = all.firstOrNull { it.id == calendar.id }?.sortOrder ?: ((all.maxOfOrNull { it.sortOrder } ?: -1) + 1)
        if (calendar.defaultForAlarms) {
            all.filter { it.defaultForAlarms && it.id != calendar.id }.forEach { dao.upsert(it.copy(defaultForAlarms = false)) }
        }
        // Never leave no default: unmarking the only one is ignored.
        val keepDefault = !calendar.defaultForAlarms && all.none { it.defaultForAlarms && it.id != calendar.id }
        dao.upsert(calendar.copy(sortOrder = sortOrder, defaultForAlarms = calendar.defaultForAlarms || keepDefault))
    }

    suspend fun setShown(id: String, shown: Boolean) = mutex.withLock {
        dao.get(id)?.let { dao.upsert(it.copy(shown = shown)) }
    }

    /**
     * Deletes calendar [id] after moving its items and alarms to [moveTo] (which also inherits
     * "default for alarms"). Refused, returning false, for the last calendar or a bad target.
     */
    suspend fun delete(id: String, moveTo: String): Boolean = mutex.withLock {
        val gone = dao.get(id) ?: return false
        val target = dao.get(moveTo)?.takeIf { it.id != id } ?: return false
        events.moveCalendar(id, target.id)
        schedules.moveCalendar(id, target.id)
        if (gone.defaultForAlarms) dao.upsert(target.copy(defaultForAlarms = true))
        dao.delete(id)
        true
    }
}
