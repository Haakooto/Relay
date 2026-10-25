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

    // Calendar reminders (spec §10b): monthly and yearly repeats, a start bound, a lead time.

    @Test fun monthlyOn31stSkipsShortMonths() {
        val s = schedule().copy(repeatMonthDay = 31)
        assertEquals(at("2026-10-31T07:00"), next(s, "2026-09-30T06:00"))
        assertEquals(at("2026-12-31T07:00"), next(s, "2026-10-31T08:00")) // no 31 November
    }

    @Test fun yearly29FebruaryOnlyInLeapYears() =
        assertEquals(at("2028-02-29T07:00"), next(schedule().copy(repeatMonthDay = 29, repeatMonth = 2), "2026-09-30T06:00"))

    @Test fun yearlyIsThatMonthOnly() =
        assertEquals(at("2027-03-15T07:00"), next(schedule().copy(repeatMonthDay = 15, repeatMonth = 3), "2026-09-30T06:00"))

    @Test fun leadCrossesMidnight() {
        val s = schedule(hour = 0, minute = 10, oneOff = "2026-10-01").copy(leadMinutes = 15)
        assertEquals(at("2026-09-30T23:55"), next(s, "2026-09-30T06:00"))
    }

    @Test fun monthlyLeadCrossesMonthEnd() {
        val s = schedule(hour = 0, minute = 10).copy(repeatMonthDay = 1, leadMinutes = 15)
        assertEquals(at("2026-09-30T23:55"), next(s, "2026-09-30T06:00"))
    }

    @Test fun leadAlreadyPassedMovesToNextOccurrence() =
        // Today's 07:00 is ahead, but its 06:00 reminder isn't; next is Friday's.
        assertEquals(at("2026-10-02T06:00"), next(schedule(days = setOf(WEDNESDAY, FRIDAY)).copy(leadMinutes = 60), "2026-09-30T06:30"))

    @Test fun startBoundHoldsBackTheRepeat() {
        val s = schedule(days = setOf(WEDNESDAY)).copy(startEpochDay = day("2026-10-14"))
        assertEquals(at("2026-10-14T07:00"), next(s, "2026-09-30T06:00"))
    }

    @Test fun monthlyIsRepeating() {
        assertEquals(true, schedule().copy(repeatMonthDay = 5).isRepeating)
        assertEquals(false, schedule(oneOff = "2026-10-01").isRepeating)
    }

    @Test fun recurrenceRules() {
        val d = LocalDate.parse("2026-04-30")
        assertEquals(true, Recurrence.occursOn(d, 0, 30, null))
        assertEquals(false, Recurrence.occursOn(d, 0, 31, null))
        assertEquals(true, Recurrence.occursOn(d, 0, 30, 4))
        assertEquals(false, Recurrence.occursOn(d, 0, 30, 5))
        assertEquals(true, Recurrence.occursOn(d, WeekdayMask.of(setOf(java.time.DayOfWeek.THURSDAY)), null, null))
    }

    // Repeat rules (#43): only schedules that carry one take this path.

    private fun ruled(rule: String, start: String, lead: Int = 0, end: String? = null) =
        schedule(hour = 10).copy(repeatRule = rule, startEpochDay = day(start), leadMinutes = lead, endEpochDay = end?.let(::day))

    @Test fun ruleGivesTheNextDate() =
        // The 2nd Tuesday: 13 October 2026, then 10 November.
        assertEquals(at("2026-11-10T10:00"), next(ruled("FREQ=MONTHLY;BYDAY=2TU", "2026-10-13"), "2026-10-14T00:00"))

    @Test fun ruleTodayIfStillAhead() =
        assertEquals(at("2026-09-30T10:00"), next(ruled("FREQ=DAILY;INTERVAL=2", "2026-09-28"), "2026-09-30T06:00"))

    @Test fun ruleIntervalKeepsItsStep() =
        // Every 2nd day from Monday 28 Sep: 28, 30 Sep, 2 Oct. After 30 Sep 10:00, next is 2 Oct.
        assertEquals(at("2026-10-02T10:00"), next(ruled("FREQ=DAILY;INTERVAL=2", "2026-09-28"), "2026-09-30T11:00"))

    @Test fun ruleWithLeadCanFireTheDayBefore() =
        assertEquals(at("2026-10-12T10:00"), next(ruled("FREQ=MONTHLY;BYDAY=2TU", "2026-10-13", lead = 24 * 60), "2026-10-01T00:00"))

    @Test fun ruleCountIsUsedUp() =
        // Three weekly occurrences from 16 Sep: 16, 23, 30 Sep. Once 30 Sep has gone, nothing.
        assertNull(next(ruled("FREQ=WEEKLY;COUNT=3", "2026-09-16"), "2026-09-30T11:00"))

    @Test fun ruleEndDateIsInclusive() =
        assertNull(next(ruled("FREQ=DAILY", "2026-09-01", end = "2026-09-30"), "2026-09-30T11:00"))

    @Test fun ruleExceptionsStillSkip() =
        assertEquals(at("2026-10-02T10:00"), next(ruled("FREQ=DAILY", "2026-09-01").copy(id = "s"), "2026-09-30T11:00", skip("2026-10-01")))

    @Test fun ruleMakesItRepeating() = assertEquals(true, ruled("FREQ=DAILY", "2026-09-01").isRepeating)
}
