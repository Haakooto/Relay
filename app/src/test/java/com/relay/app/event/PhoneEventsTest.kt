package com.relay.app.event

import com.relay.app.data.event.EventEntity
import com.relay.app.data.event.EventKind
import com.relay.app.data.event.RepeatKind
import com.relay.app.data.schedule.WeekdayMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class PhoneEventsTest {
    private val zone = ZoneId.of("Europe/Oslo")
    private fun d(text: String) = LocalDate.parse(text)
    private fun utc(text: String) = LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun local(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun event(
        start: String = "2026-10-31",
        startMinute: Int? = 10 * 60,
        end: String? = null,
        endMinute: Int? = null,
        repeat: RepeatKind = RepeatKind.NONE,
        days: Int = 0,
        until: String? = null,
    ) = EventEntity(
        "e", EventKind.EVENT, "Dentist", "Bring the forms", d(start).toEpochDay(), startMinute, end?.let { d(it).toEpochDay() },
        endMinute, repeat, days, until?.let { d(it).toEpochDay() }, 0,
    )

    @Test fun timedOneOffHasStartAndEndInItsZone() {
        val f = PhoneEvents.fields(event(endMinute = 11 * 60), zone)
        assertEquals(local("2026-10-31T10:00"), f.dtStart)
        assertEquals(local("2026-10-31T11:00"), f.dtEnd)
        assertNull(f.duration)
        assertNull(f.rrule)
        assertEquals("Europe/Oslo", f.timeZone)
    }

    @Test fun noEndMeansAnHour() =
        assertEquals(local("2026-10-31T11:00"), PhoneEvents.fields(event(), zone).dtEnd)

    @Test fun allDayIsUtcMidnightToTheDayAfterTheLast() {
        val f = PhoneEvents.fields(event(startMinute = null, end = "2026-11-02"), zone)
        assertTrue(f.allDay)
        assertEquals(utc("2026-10-31"), f.dtStart)
        assertEquals(utc("2026-11-03"), f.dtEnd)
        assertEquals("UTC", f.timeZone)
    }

    @Test fun repeatingEventsUseDurationNotEnd() {
        val f = PhoneEvents.fields(event(endMinute = 10 * 60 + 30, repeat = RepeatKind.MONTHLY), zone)
        assertNull(f.dtEnd)
        assertEquals("PT30M", f.duration)
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=31", f.rrule)
    }

    @Test fun weeklyAndYearlyRules() {
        val days = WeekdayMask.of(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY))
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE", PhoneEvents.rrule(event(repeat = RepeatKind.WEEKLY, days = days), zone))
        val leap = event(start = "2028-02-29", repeat = RepeatKind.YEARLY)
        assertEquals("FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=29", PhoneEvents.rrule(leap, zone))
    }

    @Test fun untilIsTheEndOfTheLastDayInUtc() {
        // 1 Dec 2026 00:00 in Oslo is 30 Nov 23:00 UTC; a second before.
        val monthly = event(repeat = RepeatKind.MONTHLY, until = "2026-11-30")
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=31;UNTIL=20261130T225959Z", PhoneEvents.rrule(monthly, zone))
    }

    @Test fun relaysOwnCopiesAreLeftOut() {
        val mine = PhoneInstance(
            7, 1, "Dentist", local("2026-10-31T10:00"), local("2026-10-31T11:00"), allDay = false, color = 0xFF0000FF,
        )
        val theirs = mine.copy(eventId = 8, title = "Lunch")
        val day = PhoneEvents.entries(listOf(mine, theirs), hidden = setOf(7L), d("2026-10-31"), d("2026-10-31"), zone)
            .getValue(d("2026-10-31"))
        assertEquals(listOf("Lunch"), day.map { it.title })
        assertEquals(EntryKind.PHONE, day.single().kind)
        assertEquals(phoneCalendarKey(1), day.single().calendarId)
    }

    @Test fun allDayPhoneEventsSpanTheirDaysOnly() {
        // Two all-day days: 31 Oct and 1 Nov; the end is exclusive.
        val trip = PhoneInstance(9, 1, "Trip", utc("2026-10-31"), utc("2026-11-02"), true, 0xFF0000FF)
        val byDay = PhoneEvents.entries(listOf(trip), emptySet(), d("2026-10-30"), d("2026-11-03"), zone)
        assertEquals(setOf(d("2026-10-31"), d("2026-11-01")), byDay.keys)
        assertEquals(d("2026-11-01"), byDay.getValue(d("2026-10-31")).single().spanEnd)
    }

    @Test fun phoneKeysAreKeptApartFromRelaysCalendars() =
        assertEquals(setOf(3L), phoneCalendarIds(listOf("personal", phoneCalendarKey(3), "phone:x")))
}
