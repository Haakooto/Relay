package com.relay.app.event

import com.relay.app.data.event.RepeatKind
import com.relay.app.data.schedule.WeekdayMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RepeatsTest {
    private fun d(text: String) = LocalDate.parse(text)

    // Tuesday 13 October 2026 is the 2nd Tuesday; Friday 30 October the last Friday.
    private val secondTuesday = d("2026-10-13")
    private val lastFriday = d("2026-10-30")

    @Test fun simpleRepeatsKeepTheirOldFields() {
        val weekly = Repeats.store(RepeatDraft(RepeatFreq.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY)), secondTuesday)
        assertEquals(StoredRepeat(RepeatKind.WEEKLY, WeekdayMask.of(setOf(DayOfWeek.MONDAY)), null, null), weekly)
        assertEquals(RepeatKind.MONTHLY, Repeats.store(RepeatDraft(RepeatFreq.MONTHLY), secondTuesday).kind)
        assertEquals(RepeatKind.YEARLY, Repeats.store(RepeatDraft(RepeatFreq.YEARLY), secondTuesday).kind)
    }

    @Test fun everythingElseBecomesARule() {
        fun rule(d: RepeatDraft, start: LocalDate = secondTuesday) = Repeats.store(d, start).rule
        assertEquals("FREQ=DAILY;INTERVAL=3", rule(RepeatDraft(RepeatFreq.DAILY, interval = 3)))
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=TU", rule(RepeatDraft(RepeatFreq.WEEKLY, interval = 2)))
        assertEquals("FREQ=MONTHLY;BYDAY=2TU", rule(RepeatDraft(RepeatFreq.MONTHLY, on = RepeatOn.WEEKDAY)))
        assertEquals("FREQ=MONTHLY;BYDAY=-1FR", rule(RepeatDraft(RepeatFreq.MONTHLY, on = RepeatOn.LAST_WEEKDAY), lastFriday))
        assertEquals("FREQ=MONTHLY;COUNT=6;BYMONTHDAY=13", rule(RepeatDraft(RepeatFreq.MONTHLY, end = RepeatEnd.COUNT, count = 6)))
        assertEquals("FREQ=YEARLY;BYMONTH=10;BYDAY=2TU", rule(RepeatDraft(RepeatFreq.YEARLY, on = RepeatOn.WEEKDAY)))
    }

    @Test fun rulesLoadBackIntoTheEditor() {
        listOf(
            RepeatDraft(RepeatFreq.DAILY, interval = 3),
            RepeatDraft(RepeatFreq.WEEKLY, interval = 2, weekdays = setOf(DayOfWeek.TUESDAY)),
            RepeatDraft(RepeatFreq.MONTHLY, on = RepeatOn.WEEKDAY),
            RepeatDraft(RepeatFreq.YEARLY, on = RepeatOn.WEEKDAY, end = RepeatEnd.COUNT, count = 4),
        ).forEach { draft ->
            val stored = Repeats.store(draft, secondTuesday)
            assertEquals(draft, Repeats.load(stored.kind, stored.days, stored.rule, stored.until, secondTuesday))
        }
    }

    @Test fun aRuleTheEditorCantShowIsKept() {
        val loaded = Repeats.load(RepeatKind.CUSTOM, 0, "FREQ=MONTHLY;BYDAY=2TU,4TU", null, secondTuesday)
        assertEquals("FREQ=MONTHLY;BYDAY=2TU,4TU", loaded.custom)
        assertEquals("FREQ=MONTHLY;BYDAY=2TU,4TU", Repeats.store(loaded, secondTuesday).rule)
    }

    @Test fun untilOnlyWhenTheEndIsADate() {
        assertNull(Repeats.store(RepeatDraft(RepeatFreq.DAILY, until = d("2026-12-31")), secondTuesday).until)
        val byDate = RepeatDraft(RepeatFreq.DAILY, end = RepeatEnd.DATE, until = d("2026-12-31"))
        assertEquals(d("2026-12-31"), Repeats.store(byDate, secondTuesday).until)
    }

    @Test fun optionsFollowTheDate() {
        assertEquals(listOf(RepeatOn.DATE, RepeatOn.WEEKDAY), Repeats.options(secondTuesday))
        assertEquals(listOf(RepeatOn.DATE, RepeatOn.LAST_WEEKDAY), Repeats.options(lastFriday)) // the 5th Friday
        assertEquals(listOf(RepeatOn.DATE, RepeatOn.LAST_WEEKDAY, RepeatOn.LAST_DAY), Repeats.options(d("2026-10-31")))
    }
}
