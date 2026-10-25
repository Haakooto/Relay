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

internal class FakeEventDao : EventDao {
    val events = MutableStateFlow<Map<String, EventEntity>>(emptyMap())
    override fun observeAll(): Flow<List<EventEntity>> = events.map { it.values.toList() }
    override suspend fun get(id: String) = events.value[id]
    override suspend fun inCalendar(calendarId: String) = events.value.values.filter { it.calendarId == calendarId }
    override suspend fun byIcsUid(calendarId: String, uid: String) =
        events.value.values.firstOrNull { it.calendarId == calendarId && it.icsUid == uid }
    override suspend fun upsert(event: EventEntity) { events.value += event.id to event }
    override suspend fun delete(id: String) { events.value -= id }
    override suspend fun moveCalendar(from: String, to: String) {
        events.value = events.value.mapValues { (_, e) -> if (e.calendarId == from) e.copy(calendarId = to) else e }
    }
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

    // Copies in a phone calendar (#16).

    private class FakeCopies : PhoneCopies {
        val written = mutableMapOf<Long, Pair<EventEntity, Long>>()
        var nextId = 100L
        var canWrite = true
        override suspend fun write(event: EventEntity, phoneCalendarId: Long): Long? {
            if (!canWrite) return null
            val id = event.providerEventId ?: nextId++
            written[id] = event to phoneCalendarId
            return id
        }
        override suspend fun delete(providerEventId: Long) { written -= providerEventId }
    }

    private val copies = FakeCopies()
    private var target: Long? = 5L
    private val copying = EventRepository(eventDao, schedules, { 0L }, copies) { target }

    @Test fun savingWritesACopyAndRemembersIt() = runBlocking {
        copying.save(event(), null)
        val id = eventDao.get("e")?.providerEventId
        assertEquals(100L, id)
        assertEquals(5L, copies.written.getValue(100L).second)
        copying.save(event().copy(title = "Dentist, moved"), null)
        assertEquals(100L, eventDao.get("e")?.providerEventId) // updated in place, not a second copy
        assertEquals("Dentist, moved", copies.written.getValue(100L).first.title)
    }

    @Test fun noTargetRemovesTheCopy() = runBlocking {
        copying.save(event(), null)
        target = null
        copying.syncCalendar(event().calendarId)
        assertNull(eventDao.get("e")?.providerEventId)
        assertTrue(copies.written.isEmpty())
    }

    @Test fun deletingTheItemDeletesTheCopy() = runBlocking {
        copying.save(event(), null)
        copying.delete("e")
        assertTrue(copies.written.isEmpty())
    }

    @Test fun aCopyThatCantBeWrittenDoesntStopTheSave() = runBlocking {
        copies.canWrite = false
        copying.save(event(), reminder())
        assertEquals("Dentist", eventDao.get("e")?.title)
        assertNull(eventDao.get("e")?.providerEventId)
        assertEquals(1, scheduleDao.forEvent("e").size)
    }

    @Test fun aRuleItemsReminderCarriesTheRule() = runBlocking {
        val rule = event(date = "2026-10-13", startMinute = 7 * 60)
            .copy(repeatKind = RepeatKind.CUSTOM, repeatRule = "FREQ=MONTHLY;BYDAY=2TU")
        assertEquals(at("2026-10-13T07:00"), repo.save(rule, reminder(0)))
        val s = scheduleDao.forEvent("e").single()
        assertEquals("FREQ=MONTHLY;BYDAY=2TU", s.repeatRule)
        assertEquals(day("2026-10-13"), s.startEpochDay)
        clock = at("2026-10-13T07:00")
        schedules.onFired(s.id, ms("2026-10-13T07:00"), isSnooze = false)
        assertEquals(ms("2026-11-10T07:00"), arming.armed[s.id]?.triggerAt)
    }

    // Calendar files (#33).

    private fun ics(uid: String, title: String, lead: Int? = null) =
        IcsEvent(uid, title, "", LocalDate.parse("2026-10-13"), 10 * 60, null, null, null, lead)

    private suspend fun import(vararg events: IcsEvent) = repo.import(
        events.toList(), "personal",
        toEntity = { e, id, created -> Ics.toEntity(e, "personal", id, created, zone) { it } },
        reminder = { lead -> reminder(lead) },
    )

    @Test fun importingTheSameFileAgainUpdatesInPlace() = runBlocking {
        assertEquals(ImportResult(added = 2, updated = 0), import(ics("a", "Dentist"), ics("b", "Lunch")))
        val id = eventDao.events.value.values.single { it.icsUid == "a" }.id
        assertEquals(ImportResult(added = 0, updated = 1), import(ics("a", "Dentist, moved")))
        val updated = eventDao.get(id)!!
        assertEquals("Dentist, moved", updated.title)
        assertEquals(2, eventDao.events.value.size)
    }

    @Test fun aFilesAlarmBecomesTheReminderAndOneWithoutKeepsTheItems() = runBlocking {
        import(ics("a", "Dentist", lead = 30))
        val id = eventDao.events.value.values.single().id
        assertEquals(30, scheduleDao.forEvent(id).single().leadMinutes)
        import(ics("a", "Dentist"))
        assertEquals(30, scheduleDao.forEvent(id).single().leadMinutes)
    }
}
