package com.relay.app.schedule

import com.relay.app.data.schedule.AfterFire
import com.relay.app.data.schedule.ExceptionKind
import com.relay.app.data.schedule.RingOptions
import com.relay.app.data.schedule.ScheduleEntity
import com.relay.app.data.schedule.ScheduleExceptionEntity
import com.relay.app.data.schedule.TargetKind
import com.relay.app.data.schedule.WeekdayMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class NextFireTest {
    private val zone = ZoneId.of("Europe/Oslo")

    // 2026-09-30 is a Wednesday.
    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant()
    private fun day(text: String) = LocalDate.parse(text).toEpochDay()

    private fun schedule(
        hour: Int = 7,
        minute: Int = 0,
        oneOff: String? = null,
        days: Set<java.time.DayOfWeek> = emptySet(),
        end: String? = null,
        remaining: Int? = null,
        snoozedUntil: String? = null,
    ) = ScheduleEntity(
        id = "s", label = "", hour = hour, minute = minute,
        oneOffEpochDay = oneOff?.let(::day), repeatDays = WeekdayMask.of(days),
        endEpochDay = end?.let(::day), remainingCount = remaining,
        afterFire = AfterFire.DISABLE, enabled = true, target = TargetKind.RING,
        ring = RingOptions(null, true, 0, 10, null),
        createdAtMillis = 0, createdBy = "user",
        snoozedUntilMillis = snoozedUntil?.let { at(it).toEpochMilli() }, snoozesUsed = 0, nextFireAtMillis = null,
    )

    private fun skip(from: String, to: String = from) =
        ScheduleExceptionEntity("e-$from", "s", day(from), day(to), ExceptionKind.SKIP, null, null)

    private fun retime(from: String, hour: Int, minute: Int, to: String = from) =
        ScheduleExceptionEntity("r-$from-$hour", "s", day(from), day(to), ExceptionKind.RETIME, hour, minute)

    private fun next(s: ScheduleEntity, now: String, vararg ex: ScheduleExceptionEntity) =
        NextFire.next(s, ex.toList(), at(now), zone)

    @Test fun oneOffLaterToday() =
        assertEquals(at("2026-09-30T07:00"), next(schedule(oneOff = "2026-09-30"), "2026-09-30T06:00"))

    @Test fun oneOffInThePastNeverFires() =
        assertNull(next(schedule(oneOff = "2026-09-30"), "2026-09-30T07:00"))

    @Test fun oneOffSkipped() =
        assertNull(next(schedule(oneOff = "2026-10-01"), "2026-09-30T12:00", skip("2026-10-01")))

    @Test fun oneOffRetimed() =
        assertEquals(at("2026-10-01T09:30"), next(schedule(oneOff = "2026-10-01"), "2026-09-30T12:00", retime("2026-10-01", 9, 30)))

    @Test fun repeatingPicksNextMatchingWeekday() =
        assertEquals(at("2026-10-02T07:00"), next(schedule(days = setOf(MONDAY, FRIDAY)), "2026-09-30T12:00"))

    @Test fun repeatingTodayIfStillAhead() =
        assertEquals(at("2026-09-30T07:00"), next(schedule(days = setOf(WEDNESDAY)), "2026-09-30T06:59"))

    @Test fun repeatingExactlyNowMovesToNextWeek() =
        assertEquals(at("2026-10-07T07:00"), next(schedule(days = setOf(WEDNESDAY)), "2026-09-30T07:00"))

    @Test fun skipRangeCoversVacationWeek() =
        assertEquals(
            at("2026-10-12T07:00"),
            next(schedule(days = setOf(MONDAY, WEDNESDAY, FRIDAY)), "2026-09-30T12:00", skip("2026-10-01", "2026-10-11")),
        )

    @Test fun retimeOnlyChangesThatDay() {
        val s = schedule(days = setOf(WEDNESDAY, FRIDAY))
        assertEquals(at("2026-10-02T10:15"), next(s, "2026-09-30T12:00", retime("2026-10-02", 10, 15)))
        assertEquals(at("2026-10-07T07:00"), next(s, "2026-10-02T10:15", retime("2026-10-02", 10, 15)))
    }

    @Test fun skipWinsOverRetime() =
        assertEquals(
            at("2026-10-07T07:00"),
            next(schedule(days = setOf(WEDNESDAY, FRIDAY)), "2026-09-30T12:00", retime("2026-10-02", 9, 0), skip("2026-10-02")),
        )

    @Test fun retimeEarlierThanNowOnTheDayIsPast() =
        assertEquals(
            at("2026-10-07T07:00"),
            next(schedule(days = setOf(WEDNESDAY)), "2026-09-30T06:00", retime("2026-09-30", 5, 0)),
        )

    @Test fun endDateIsInclusiveThenStops() {
        val s = schedule(days = setOf(WEDNESDAY), end = "2026-10-07")
        assertEquals(at("2026-10-07T07:00"), next(s, "2026-09-30T12:00"))
        assertNull(next(s, "2026-10-07T07:00"))
    }

    @Test fun countExhaustedNeverFires() =
        assertNull(next(schedule(days = setOf(WEDNESDAY), remaining = 0), "2026-09-30T06:00"))

    @Test fun snoozeWinsWhileAhead() {
        val s = schedule(days = setOf(WEDNESDAY), snoozedUntil = "2026-09-30T07:10")
        assertEquals(at("2026-09-30T07:10"), next(s, "2026-09-30T07:01"))
        assertEquals(at("2026-10-07T07:00"), next(s, "2026-09-30T07:10"))
    }

    @Test fun dstSpringForwardGapShiftsLater() =
        // 2027-03-28 02:30 doesn't exist in Oslo (02:00 -> 03:00): fires at 03:30, the same instant.
        assertEquals(
            LocalDateTime.parse("2027-03-28T03:30").atZone(zone).toInstant(),
            next(schedule(hour = 2, minute = 30, days = setOf(SUNDAY)), "2027-03-27T12:00"),
        )

    @Test fun nextDateForOneOff() {
        assertEquals(LocalDate.parse("2026-09-30"), NextFire.nextDateFor(7, 0, at("2026-09-30T06:00"), zone))
        assertEquals(LocalDate.parse("2026-10-01"), NextFire.nextDateFor(7, 0, at("2026-09-30T07:00"), zone))
    }
}
