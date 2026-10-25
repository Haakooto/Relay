package com.relay.app.event

import com.relay.app.data.event.CalendarDao
import com.relay.app.data.event.CalendarEntity
import com.relay.app.data.event.EventEntity
import com.relay.app.data.event.EventKind
import com.relay.app.data.event.RepeatKind
import com.relay.app.data.schedule.AfterFire
import com.relay.app.data.schedule.RingOptions
import com.relay.app.data.schedule.ScheduleEntity
import com.relay.app.data.schedule.TargetKind
import com.relay.app.data.settings.AppSettings
import com.relay.app.schedule.FakeScheduleArming
import com.relay.app.schedule.FakeScheduleDao
import com.relay.app.schedule.FakeScheduleNotices
import com.relay.app.schedule.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeCalendarDao : CalendarDao {
    val calendars = MutableStateFlow<Map<String, CalendarEntity>>(emptyMap())
    override fun observeAll(): Flow<List<CalendarEntity>> = calendars.map { all -> all.values.sortedBy { it.sortOrder } }
    override suspend fun getAll() = calendars.value.values.sortedBy { it.sortOrder }
    override suspend fun get(id: String) = calendars.value[id]
    override suspend fun upsert(calendar: CalendarEntity) { calendars.value += calendar.id to calendar }
    override suspend fun delete(id: String) { calendars.value -= id }
}

class CalendarRepositoryTest {
    private val dao = FakeCalendarDao()
    private val events = FakeEventDao()
    private val scheduleDao = FakeScheduleDao()
    private val schedules =
        ScheduleRepository(scheduleDao, FakeScheduleArming(), FakeScheduleNotices(), { AppSettings() })
    private val repo = CalendarRepository(dao, events, schedules)

    private fun calendar(id: String, order: Int, alarms: Boolean = false) = CalendarEntity(
        id, id, 0xFF1F6FB0, order, shown = true, defaultForAlarms = alarms,
        defaultLeadMinutes = null, defaultTarget = TargetKind.NOTIFY, defaultRepeat = RepeatKind.NONE,
    )

    private fun event(id: String, calendarId: String) = EventEntity(
        id, EventKind.EVENT, id, "", 0, null, null, null, RepeatKind.NONE, 0, null, 0, calendarId = calendarId,
    )

    private fun alarm(id: String, calendarId: String) = ScheduleEntity(
        id = id, label = "", hour = 7, minute = 0, oneOffEpochDay = null, repeatDays = 1, endEpochDay = null,
        remainingCount = null, afterFire = AfterFire.DISABLE, enabled = false, target = TargetKind.RING,
        ring = RingOptions(null, true, 0, 10, null), createdAtMillis = 0, createdBy = "user",
        snoozedUntilMillis = null, snoozesUsed = 0, nextFireAtMillis = null, calendarId = calendarId,
    )

    private fun seed() = runBlocking {
        dao.upsert(calendar("personal", 0))
        dao.upsert(calendar("alarms", 1, alarms = true))
    }

    @Test fun deletingMovesItemsAndAlarmsAndTheAlarmDefault() = runBlocking {
        seed()
        events.upsert(event("e", "alarms"))
        scheduleDao.upsert(alarm("a", "alarms"))
        assertTrue(repo.delete("alarms", moveTo = "personal"))
        assertNull(dao.get("alarms"))
        assertEquals("personal", events.get("e")?.calendarId)
        assertEquals("personal", scheduleDao.get("a")?.calendarId)
        assertTrue(dao.get("personal")!!.defaultForAlarms)
    }

    @Test fun cantDeleteIntoItselfOrNowhere() = runBlocking {
        seed()
        assertFalse(repo.delete("personal", moveTo = "personal"))
        assertFalse(repo.delete("personal", moveTo = "gone"))
        assertEquals(2, dao.getAll().size)
    }

    @Test fun markingADefaultForAlarmsUnmarksTheOthers() = runBlocking {
        seed()
        repo.save(dao.get("personal")!!.copy(defaultForAlarms = true))
        assertEquals(listOf("personal"), dao.getAll().filter { it.defaultForAlarms }.map { it.id })
    }

    @Test fun theOnlyDefaultForAlarmsCantBeUnmarked() = runBlocking {
        seed()
        repo.save(dao.get("alarms")!!.copy(defaultForAlarms = false))
        assertTrue(dao.get("alarms")!!.defaultForAlarms)
    }

    @Test fun aNewCalendarGoesLast() = runBlocking {
        seed()
        repo.save(calendar("work", 0))
        assertEquals(listOf("personal", "alarms", "work"), dao.getAll().map { it.id })
    }
}
