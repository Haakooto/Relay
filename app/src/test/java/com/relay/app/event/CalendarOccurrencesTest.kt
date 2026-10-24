package com.relay.app.event

import com.relay.app.data.event.EventEntity
import com.relay.app.data.event.EventKind
import com.relay.app.data.event.RepeatKind
import com.relay.app.data.schedule.AfterFire
import com.relay.app.data.schedule.RingOptions
import com.relay.app.data.schedule.ScheduleEntity
import com.relay.app.data.schedule.TargetKind
import com.relay.app.data.schedule.WeekdayMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class CalendarOccurrencesTest {
    private fun d(text: String) = LocalDate.parse(text)
    private val today = d("2026-09-30") // a Wednesday

    private fun event(
        id: String = "e",
        start: String = "2026-01-31",
        startMinute: Int? = 10 * 60,
        end: String? = null,
        endMinute: Int? = null,
        repeat: RepeatKind = RepeatKind.NONE,
        until: String? = null,
    ) = EventEntity(
        id = id, kind = EventKind.EVENT, title = id, note = "", startEpochDay = d(start).toEpochDay(), startMinute = startMinute,
        endEpochDay = end?.let { d(it).toEpochDay() }, endMinute = endMinute, repeatKind = repeat, repeatDays = 0,
        untilEpochDay = until?.let { d(it).toEpochDay() }, createdAtMillis = 0,
    )

    private fun alarm(days: Set<DayOfWeek>, remaining: Int? = null) = ScheduleEntity(
        id = "a", label = "Wake", hour = 7, minute = 0, oneOffEpochDay = null, repeatDays = WeekdayMask.of(days),
        endEpochDay = null, remainingCount = remaining, afterFire = AfterFire.DISABLE, enabled = true, target = TargetKind.RING,
        ring = RingOptions(null, true, 0, 10, null), createdAtMillis = 0, createdBy = "user",
        snoozedUntilMillis = null, snoozesUsed = 0, nextFireAtMillis = null,
    )

    private fun between(
        events: List<EventEntity>,
        from: String,
        to: String,
        schedules: List<ScheduleEntity> = emptyList(),
        all: Boolean = false,
    ) = CalendarOccurrences.between(events, schedules, emptyList(), d(from), d(to), today, all)

    @Test fun monthlyOn31stSkipsMonthsWithoutIt() {
        val days = between(listOf(event(repeat = RepeatKind.MONTHLY)), "2026-02-01", "2026-05-31").keys
        assertEquals(setOf(d("2026-03-31"), d("2026-05-31")), days)
    }

    @Test fun repeatStopsAfterUntil() {
        val monthly = event(start = "2026-01-10", repeat = RepeatKind.MONTHLY, until = "2026-03-10")
        val days = between(listOf(monthly), "2026-01-01", "2026-06-30").keys
        assertEquals(setOf(d("2026-01-10"), d("2026-02-10"), d("2026-03-10")), days)
    }

    @Test fun multiDayEventShowsOnEachDayAcrossTheMonthEdge() {
        val trip = event(start = "2026-09-29", end = "2026-10-02", endMinute = 12 * 60)
        val byDay = between(listOf(trip), "2026-10-01", "2026-10-31")
        assertEquals(setOf(d("2026-10-01"), d("2026-10-02")), byDay.keys)
        val last = byDay.getValue(d("2026-10-02")).single()
        assertTrue(last.continued)
        assertNull(last.startMinute)
        assertEquals(12 * 60, last.endMinute)
    }

    @Test fun allDayFirstThenByTime() {
        val events = listOf(
            event("late", start = "2026-10-01", startMinute = 18 * 60),
            event("allday", start = "2026-10-01", startMinute = null),
            event("early", start = "2026-10-01", startMinute = 8 * 60),
        )
        assertEquals(listOf("allday", "early", "late"), between(events, "2026-10-01", "2026-10-01").getValue(d("2026-10-01")).map { it.id })
    }

    @Test fun alarmsOnlyWhenAskedAndFromToday() {
        val wake = alarm(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        assertTrue(between(emptyList(), "2026-09-28", "2026-10-04", listOf(wake)).isEmpty())
        val days = between(emptyList(), "2026-09-28", "2026-10-04", listOf(wake), all = true).keys
        assertEquals(setOf(d("2026-09-30")), days) // Monday 28th is before today
    }

    @Test fun countLimitedAlarmIsCountedFromToday() {
        val wake = alarm(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), remaining = 3)
        val days = between(emptyList(), "2026-10-05", "2026-10-31", listOf(wake), all = true).keys
        // Today (Wed 30th) and Mon 5th, Wed 7th use the three.
        assertEquals(setOf(d("2026-10-05"), d("2026-10-07")), days)
    }

    @Test fun eventReminderLeadShowsOnItsRow() {
        val reminder = alarm(emptySet())
            .copy(id = "r", eventId = "e", leadMinutes = 15, target = TargetKind.NOTIFY, oneOffEpochDay = d("2026-10-01").toEpochDay())
        val entry = between(listOf(event(start = "2026-10-01")), "2026-10-01", "2026-10-01", listOf(reminder), all = true)
            .getValue(d("2026-10-01")).single()
        assertEquals(15, entry.reminderLead)
        assertEquals(EntryKind.EVENT, entry.kind)
    }
}
