package com.relay.app.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class RruleTest {
    private fun d(text: String) = LocalDate.parse(text)

    private fun dates(rule: String, start: String, last: String = "2030-12-31", until: String? = null): List<LocalDate> {
        val (r, ruleUntil) = Rrule.parse(rule) ?: error("didn't parse: $rule")
        return RruleDates.dates(r, d(start), until?.let(::d) ?: ruleUntil, d(last)).toList()
    }

    private fun days(vararg text: String) = text.map(::d)

    @Test fun parsesAndFormatsTheSupportedSet() {
        val (rule, until) = Rrule.parse("RRULE:FREQ=MONTHLY;INTERVAL=2;BYDAY=-1FR;COUNT=5;UNTIL=20261231")!!
        assertEquals("FREQ=MONTHLY;INTERVAL=2;COUNT=5;BYDAY=-1FR", rule.format())
        assertEquals(d("2026-12-31"), until)
        assertEquals(listOf(WeekdayNum(DayOfWeek.FRIDAY, -1)), rule.byDay)
    }

    @Test fun untilAsUtcTimeBecomesALocalDay() {
        // 30 Nov 23:30 UTC is already 1 December in Oslo.
        val (_, until) = Rrule.parse("FREQ=DAILY;UNTIL=20261130T233000Z", ZoneId.of("Europe/Oslo"))!!
        assertEquals(d("2026-12-01"), until)
    }

    @Test fun outsideTheSetDoesntParse() {
        assertNull(Rrule.parse("FREQ=HOURLY"))
        assertNull(Rrule.parse("FREQ=MONTHLY;BYSETPOS=-1;BYDAY=MO,TU"))
        assertNull(Rrule.parse("FREQ=YEARLY;BYWEEKNO=20"))
        assertNull(Rrule.parse("FREQ=WEEKLY;BYDAY=XX"))
        assertNull(Rrule.parse("FREQ=MONTHLY;BYMONTHDAY=0"))
        assertNull(Rrule.parse("nonsense"))
    }

    @Test fun everyOtherDayStopsAtCount() =
        assertEquals(days("2026-10-01", "2026-10-03", "2026-10-05"), dates("FREQ=DAILY;INTERVAL=2;COUNT=3", "2026-10-01"))

    @Test fun everyOtherWeekOnTwoDays() =
        // Thu 1 Oct 2026: its week's Monday is 28 Sep, so Monday 28 Sep is before the start and skipped.
        assertEquals(
            days("2026-10-01", "2026-10-12", "2026-10-15", "2026-10-26", "2026-10-29"),
            dates("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TH", "2026-10-01", last = "2026-10-31"),
        )

    @Test fun weeklyWithoutDaysIsTheStartsWeekday() =
        assertEquals(days("2026-10-01", "2026-10-08"), dates("FREQ=WEEKLY", "2026-10-01", last = "2026-10-14"))

    @Test fun secondTuesdayOfTheMonth() =
        assertEquals(days("2026-10-13", "2026-11-10", "2026-12-08"), dates("FREQ=MONTHLY;BYDAY=2TU", "2026-10-13", last = "2026-12-31"))

    @Test fun lastFridayOfTheMonth() =
        assertEquals(days("2026-10-30", "2026-11-27", "2026-12-25"), dates("FREQ=MONTHLY;BYDAY=-1FR", "2026-10-30", last = "2026-12-31"))

    @Test fun lastDayOfTheMonth() =
        assertEquals(days("2027-01-31", "2027-02-28", "2027-03-31"), dates("FREQ=MONTHLY;BYMONTHDAY=-1", "2027-01-31", last = "2027-03-31"))

    @Test fun firstAndFifteenth() =
        assertEquals(
            days("2026-10-15", "2026-11-01", "2026-11-15"),
            dates("FREQ=MONTHLY;BYMONTHDAY=1,15", "2026-10-10", last = "2026-11-30"),
        )

    @Test fun monthlyOnThe31stSkipsShortMonths() =
        assertEquals(days("2026-10-31", "2026-12-31"), dates("FREQ=MONTHLY", "2026-10-31", last = "2026-12-31"))

    @Test fun yearlyOn29FebruaryOnlyInLeapYears() =
        assertEquals(days("2028-02-29"), dates("FREQ=YEARLY", "2028-02-29", last = "2031-12-31"))

    @Test fun thanksgivingIsTheFourthThursdayOfNovember() =
        assertEquals(days("2026-11-26", "2027-11-25"), dates("FREQ=YEARLY;BYMONTH=11;BYDAY=4TH", "2026-11-26", last = "2027-12-31"))

    @Test fun untilIsInclusive() =
        assertEquals(days("2026-10-01", "2026-10-02"), dates("FREQ=DAILY", "2026-10-01", until = "2026-10-02"))

    @Test fun countIsCountedFromTheStartNotFromLater() {
        val (rule, _) = Rrule.parse("FREQ=WEEKLY;COUNT=2")!!
        assertEquals(days("2026-10-01", "2026-10-08"), RruleDates.dates(rule, d("2026-10-01"), null, d("2030-01-01")).toList())
    }

    @Test fun aRuleThatNeverLandsEnds() =
        assertEquals(emptyList<LocalDate>(), dates("FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30", "2026-01-01"))
}
