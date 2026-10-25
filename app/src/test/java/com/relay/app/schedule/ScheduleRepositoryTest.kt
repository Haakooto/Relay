package com.relay.app.schedule

import com.relay.app.data.schedule.AfterFire
import com.relay.app.data.schedule.ExceptionKind
import com.relay.app.data.schedule.RingOptions
import com.relay.app.data.schedule.ScheduleEntity
import com.relay.app.data.schedule.TargetKind
import com.relay.app.data.schedule.WeekdayMask
import com.relay.app.data.settings.AppSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleRepositoryTest {
    private val zone = ZoneId.of("Europe/Oslo")
    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant()
    private fun ms(text: String) = at(text).toEpochMilli()

    private var clock = at("2026-09-30T06:00") // a Wednesday
    private val dao = FakeScheduleDao()
    private val arming = FakeScheduleArming()
    private val notices = FakeScheduleNotices()
    private var settings = AppSettings(upcomingLeadMinutes = 0)
    private val repo = ScheduleRepository(dao, arming, notices, { settings }, { clock }, { zone })

    private fun alarm(
        id: String = "a",
        days: Set<DayOfWeek> = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
        afterFire: AfterFire = AfterFire.DISABLE,
        maxSnoozes: Int? = null,
    ) = ScheduleEntity(
        id = id, label = "", hour = 7, minute = 0,
        oneOffEpochDay = null, repeatDays = WeekdayMask.of(days),
        endEpochDay = null, remainingCount = null, afterFire = afterFire, enabled = true,
        target = TargetKind.RING, ring = RingOptions(null, true, 0, 10, maxSnoozes),
        createdAtMillis = 0, createdBy = "user", snoozedUntilMillis = null, snoozesUsed = 0, nextFireAtMillis = null,
    )

    @Test fun saveArmsNextOccurrence() = runBlocking {
        repo.save(alarm())
        assertEquals(ms("2026-09-30T07:00"), arming.armed["a"]?.triggerAt)
        assertEquals(ms("2026-09-30T07:00"), dao.get("a")?.nextFireAtMillis)
    }

    @Test fun fireRingsAndArmsTomorrowBeforeRinging() = runBlocking {
        repo.save(alarm())
        clock = at("2026-09-30T07:00")
        val decision = repo.onFired("a", ms("2026-09-30T07:00"), isSnooze = false)
        assertTrue(decision is FireDecision.Ring)
        assertEquals(ms("2026-10-01T07:00"), arming.armed["a"]?.triggerAt)
    }

    /** The race that silenced alarms: the process starts because of the alarm, and the start-up
     *  reschedule runs before the fire is handled. The alarm must still ring. */
    @Test fun startupRescheduleBeforeFireStillRings() = runBlocking {
        repo.save(alarm())
        clock = at("2026-09-30T07:00:01")
        repo.rescheduleAll()
        assertEquals(ms("2026-09-30T07:00"), dao.get("a")?.nextFireAtMillis) // not advanced
        val decision = repo.onFired("a", ms("2026-09-30T07:00"), isSnooze = false)
        assertTrue(decision is FireDecision.Ring)
    }

    @Test fun duplicateFireIsIgnored() = runBlocking {
        repo.save(alarm())
        clock = at("2026-09-30T07:00")
        repo.onFired("a", ms("2026-09-30T07:00"), isSnooze = false)
        assertEquals(FireDecision.Ignore, repo.onFired("a", ms("2026-09-30T07:00"), isSnooze = false))
    }

    @Test fun deletedOrDisabledAlarmDoesNotRing() = runBlocking {
        repo.save(alarm())
        repo.setEnabled("a", false)
        assertNull(arming.armed["a"])
        assertEquals(FireDecision.Ignore, repo.onFired("a", ms("2026-09-30T07:00"), isSnooze = false))
        repo.delete("a")
        assertEquals(FireDecision.Ignore, repo.onFired("a", ms("2026-09-30T07:00"), isSnooze = false))
    }

    @Test fun snoozeArmsSnoozeThenDismissRestoresNextOccurrence() = runBlocking {
        repo.save(alarm())
        clock = at("2026-09-30T07:00")
        repo.onFired("a", ms("2026-09-30T07:00"), isSnooze = false)
        repo.onSnoozed("a")
        assertEquals(FakeScheduleArming.Armed(ms("2026-09-30T07:10"), true, ms("2026-09-30T07:10")), arming.armed["a"])
        clock = at("2026-09-30T07:10")
        assertTrue(repo.onFired("a", ms("2026-09-30T07:10"), isSnooze = true) is FireDecision.Ring)
        repo.onDismissed("a")
        assertEquals(ms("2026-10-01T07:00"), arming.armed["a"]?.triggerAt)
        assertEquals(0, dao.get("a")?.snoozesUsed)
    }

    @Test fun maxSnoozesHidesSnooze() = runBlocking {
        repo.save(alarm(maxSnoozes = 1))
        clock = at("2026-09-30T07:00")
        assertTrue((repo.onFired("a", ms("2026-09-30T07:00"), false) as FireDecision.Ring).item.canSnooze)
        repo.onSnoozed("a")
        clock = at("2026-09-30T07:10")
        assertFalse((repo.onFired("a", ms("2026-09-30T07:10"), true) as FireDecision.Ring).item.canSnooze)
    }

    @Test fun maxSnoozesNoneNeverOffersSnooze() = runBlocking {
        repo.save(alarm(maxSnoozes = 0))
        clock = at("2026-09-30T07:00")
        assertFalse((repo.onFired("a", ms("2026-09-30T07:00"), false) as FireDecision.Ring).item.canSnooze)
    }

    @Test fun oneOffDeleteAfterDismiss() = runBlocking {
        repo.save(alarm(days = emptySet(), afterFire = AfterFire.DELETE))
        clock = at("2026-09-30T07:00")
        repo.onFired("a", ms("2026-09-30T07:00"), false)
        assertTrue(dao.get("a") != null) // kept while ringing, so snooze still works
        repo.onDismissed("a")
        assertNull(dao.get("a"))
    }

    @Test fun oneOffDisableAfterDismiss() = runBlocking {
        repo.save(alarm(days = emptySet(), afterFire = AfterFire.DISABLE))
        clock = at("2026-09-30T07:00")
        repo.onFired("a", ms("2026-09-30T07:00"), false)
        repo.onDismissed("a")
        assertEquals(false, dao.get("a")?.enabled)
        assertNull(arming.armed["a"])
    }

    @Test fun missedWithinAnHourRingsLate() = runBlocking {
        repo.save(alarm())
        clock = at("2026-09-30T07:40") // phone was off at 07:00
        repo.rescheduleAll()
        assertEquals(FakeScheduleArming.Armed(ms("2026-09-30T07:00"), false, ms("2026-09-30T07:40")), arming.armed["a"])
        assertTrue(repo.onFired("a", ms("2026-09-30T07:00"), false) is FireDecision.Ring)
    }

    @Test fun missedByMoreThanAnHourOnlyNotifies() = runBlocking {
        repo.save(alarm())
        clock = at("2026-09-30T09:00")
        repo.rescheduleAll()
        assertTrue("missed:a" in notices.shown)
        assertEquals(ms("2026-10-01T07:00"), arming.armed["a"]?.triggerAt)
    }

    @Test fun skipNextAddsExceptionForThatDay() = runBlocking {
        repo.save(alarm())
        repo.skipNext("a")
        assertEquals(ms("2026-10-01T07:00"), arming.armed["a"]?.triggerAt)
        assertEquals(ExceptionKind.SKIP, dao.exceptionsFor("a").single().kind)
    }

    @Test fun bulkExceptionAppliesToEveryPickedAlarm() = runBlocking {
        repo.save(alarm("a"))
        repo.save(alarm("b"))
        repo.addException(listOf("a", "b"), java.time.LocalDate.parse("2026-09-30"), java.time.LocalDate.parse("2026-10-01"), ExceptionKind.SKIP, null, null)
        assertEquals(ms("2026-10-07T07:00"), arming.armed["a"]?.triggerAt)
        assertEquals(ms("2026-10-07T07:00"), arming.armed["b"]?.triggerAt)
    }

    @Test fun upcomingArmedAheadOrShownWhenInsideLead() = runBlocking {
        settings = AppSettings(upcomingLeadMinutes = 120)
        repo.save(alarm())
        assertTrue("upcoming:a" in notices.shown) // 06:00 is inside the 2 h lead for 07:00
        clock = at("2026-09-30T07:00")
        repo.onFired("a", ms("2026-09-30T07:00"), false)
        assertEquals(ms("2026-10-01T05:00"), arming.upcoming["a"])
    }

    @Test fun timerTargetStartsQuietlyAndArmsAsNonRinging() = runBlocking {
        repo.save(alarm().copy(target = TargetKind.TIMER, targetDefId = "tea"))
        assertEquals(false, arming.lastRings)
        clock = at("2026-09-30T07:00")
        val decision = repo.onFired("a", ms("2026-09-30T07:00"), isSnooze = false)
        assertTrue(decision is FireDecision.Start)
        assertEquals("tea", (decision as FireDecision.Start).schedule.targetDefId)
        assertEquals(ms("2026-10-01T07:00"), arming.armed["a"]?.triggerAt) // next day still armed
    }

    @Test fun oneOffTargetFinishesItsSessionAtOnce() = runBlocking {
        repo.save(alarm(days = emptySet(), afterFire = AfterFire.DELETE).copy(target = TargetKind.STOPWATCH))
        clock = at("2026-09-30T07:00")
        assertTrue(repo.onFired("a", ms("2026-09-30T07:00"), false) is FireDecision.Start)
        assertNull(dao.get("a")) // nothing rings, so nothing to dismiss: deleted straight away
    }

    @Test fun targetFireIsIdempotentToo() = runBlocking {
        repo.save(alarm().copy(target = TargetKind.RELAY, targetDefId = "wake"))
        clock = at("2026-09-30T07:00")
        repo.onFired("a", ms("2026-09-30T07:00"), false)
        assertEquals(FireDecision.Ignore, repo.onFired("a", ms("2026-09-30T07:00"), false))
    }

    @Test fun targetSpecRoundTrips() {
        val spec = TargetSpec(label = "Run", t0Millis = -120_000, tags = listOf("run"))
        assertEquals(spec, TargetSpec.decode(TargetSpec.encode(spec)))
        assertEquals(TargetSpec(), TargetSpec.decode("{}"))
    }
}
