package com.relay.app.event

import com.relay.app.data.event.EventDao
import com.relay.app.data.event.EventEntity
import com.relay.app.data.event.EventKind
import com.relay.app.data.event.RepeatKind
import com.relay.app.data.schedule.RingOptions
import com.relay.app.data.schedule.TargetKind
import com.relay.app.data.settings.AppSettings
import com.relay.app.schedule.FakeScheduleArming
import com.relay.app.schedule.FakeScheduleDao
import com.relay.app.schedule.FakeScheduleNotices
import com.relay.app.schedule.FireDecision
import com.relay.app.schedule.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private class FakeEventDao : EventDao {
    val events = MutableStateFlow<Map<String, EventEntity>>(emptyMap())
    override fun observeAll(): Flow<List<EventEntity>> = events.map { it.values.toList() }
    override suspend fun get(id: String) = events.value[id]
    override suspend fun upsert(event: EventEntity) { events.value += event.id to event }
    override suspend fun delete(id: String) { events.value -= id }
}

class EventRepositoryTest {
    private val zone = ZoneId.of("Europe/Oslo")
    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant()
    private fun ms(text: String) = at(text).toEpochMilli()
    private fun day(text: String) = LocalDate.parse(text).toEpochDay()

    private var clock = at("2026-09-30T06:00") // a Wednesday
    private val scheduleDao = FakeScheduleDao()
    private val arming = FakeScheduleArming()
    private var settings = AppSettings(upcomingLeadMinutes = 120)
    private val schedules =
        ScheduleRepository(scheduleDao, arming, FakeScheduleNotices(), { settings }, { clock }, { zone })
    private val eventDao = FakeEventDao()
    private val repo = EventRepository(eventDao, schedules) { 0L }

    private fun event(
        date: String = "2026-10-01",
        startMinute: Int? = 10 * 60,
        repeat: RepeatKind = RepeatKind.NONE,
        kind: EventKind = EventKind.EVENT,
    ) = EventEntity(
        id = "e", kind = kind, title = "Dentist", note = "", startEpochDay = day(date), startMinute = startMinute,
        endEpochDay = null, endMinute = null, repeatKind = repeat, repeatDays = 0, untilEpochDay = null, createdAtMillis = 0,
    )

    private fun reminder(lead: Int = 15, target: TargetKind = TargetKind.NOTIFY) =
        ReminderDraft(lead, target, null, "{}", RingOptions(null, true, 0, 10, null))

    @Test fun reminderIsAScheduleArmedAheadOfTheStart() = runBlocking {
        val next = repo.save(event(), reminder(15))
        assertEquals(at("2026-10-01T09:45"), next)
        val s = scheduleDao.forEvent("e").single()
        assertEquals(ms("2026-10-01T09:45"), arming.armed[s.id]?.triggerAt)
        assertEquals(false, arming.lastRings) // a notification doesn't ring
    }

    @Test fun reminderIsNotAnAlarm() = runBlocking {
        repo.save(event(), reminder())
        assertTrue(schedules.observeAlarms().first().isEmpty())
    }

    @Test fun notifyGetsNoUpcomingNotice() = runBlocking {
        repo.save(event(), reminder(target = TargetKind.NOTIFY))
        assertTrue(arming.upcoming.isEmpty())
        repo.save(event(), reminder(target = TargetKind.RING))
        assertEquals(1, arming.upcoming.size)
    }

    @Test fun allDayReminderIsAtNineTheDayBefore() = runBlocking {
        assertEquals(at("2026-09-30T09:00"), repo.save(event(startMinute = null), reminder(24 * 60)))
    }

    @Test fun pastReminderStaysOnItsDayAndDoesNotFire() = runBlocking {
        val next = repo.save(event(date = "2026-09-30", startMinute = 5 * 60), reminder(0))
        assertNull(next)
        assertEquals(day("2026-09-30"), scheduleDao.forEvent("e").single().oneOffEpochDay)
        assertTrue(arming.armed.isEmpty())
    }

    @Test fun editingKeepsOneReminderAndRemovingDeletesIt() = runBlocking {
        repo.save(event(), reminder(15))
        val id = scheduleDao.forEvent("e").single().id
        repo.save(event(), reminder(30))
        assertEquals(id, scheduleDao.forEvent("e").single().id)
        assertEquals(30, scheduleDao.forEvent("e").single().leadMinutes)
        repo.save(event(), null)
        assertTrue(scheduleDao.forEvent("e").isEmpty())
        assertTrue(arming.armed.isEmpty())
    }

    @Test fun deletingTheEventDeletesItsReminder() = runBlocking {
        repo.save(event(), reminder())
        repo.delete("e")
        assertNull(eventDao.get("e"))
        assertTrue(scheduleDao.forEvent("e").isEmpty())
        assertTrue(arming.armed.isEmpty())
    }

    /** A monthly reminder must survive its first fire: a one-off would be switched off. */
    @Test fun monthlyReminderKeepsGoingAfterFiring() = runBlocking {
        repo.save(event(date = "2026-09-30", startMinute = 7 * 60, repeat = RepeatKind.MONTHLY), reminder(0))
        val id = scheduleDao.forEvent("e").single().id
        clock = at("2026-09-30T07:00")
        assertTrue(schedules.onFired(id, ms("2026-09-30T07:00"), isSnooze = false) is FireDecision.Start)
        assertEquals(true, scheduleDao.get(id)?.enabled)
        assertEquals(ms("2026-10-30T07:00"), arming.armed[id]?.triggerAt)
    }

    @Test fun oneOffReminderIsKeptSwitchedOffAfterFiring() = runBlocking {
        repo.save(event(startMinute = 7 * 60), reminder(0))
        val id = scheduleDao.forEvent("e").single().id
        clock = at("2026-10-01T07:00")
        schedules.onFired(id, ms("2026-10-01T07:00"), isSnooze = false)
        assertEquals(false, scheduleDao.get(id)?.enabled)
    }

    @Test fun repeatStartsOnTheEventsDay() = runBlocking {
        val next = repo.save(event(date = "2026-11-15", startMinute = 7 * 60, repeat = RepeatKind.YEARLY), reminder(0))
        assertEquals(at("2026-11-15T07:00"), next)
    }
}
