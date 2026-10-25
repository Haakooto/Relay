package com.relay.app.event

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class CalendarViewsTest {
    private fun d(text: String) = LocalDate.parse(text)
    private fun entry(id: String) = CalendarEntry(EntryKind.EVENT, id, id, null, null)

    @Test fun gridStartsOnTheWeekStartBeforeTheFirst() {
        // 1 October 2026 is a Thursday.
        assertEquals(d("2026-09-28"), CalendarViews.gridStart(YearMonth.of(2026, 10), DayOfWeek.MONDAY))
        assertEquals(d("2026-09-27"), CalendarViews.gridStart(YearMonth.of(2026, 10), DayOfWeek.SUNDAY))
        assertEquals(d("2026-11-08"), CalendarViews.gridEnd(YearMonth.of(2026, 10), DayOfWeek.MONDAY))
    }

    @Test fun gridStartsOnTheFirstWhenItIsTheWeekStart() =
        assertEquals(d("2026-06-01"), CalendarViews.gridStart(YearMonth.of(2026, 6), DayOfWeek.MONDAY))

    @Test fun agendaHasHeadingsForDaysWithEntriesOnly() {
        val entries = mapOf(
            d("2026-10-27") to listOf(entry("b")),
            d("2026-10-25") to listOf(entry("a"), entry("a2")),
            d("2026-10-26") to emptyList(),
        )
        val sunday = d("2026-10-25")
        val tuesday = d("2026-10-27")
        assertEquals(
            listOf(
                AgendaRow.Heading(sunday), AgendaRow.Item(sunday, entry("a")), AgendaRow.Item(sunday, entry("a2")),
                AgendaRow.Heading(tuesday), AgendaRow.Item(tuesday, entry("b")),
            ),
            CalendarViews.agenda(entries, today = sunday),
        )
    }

    @Test fun agendaIsTodayAndTheNextSixDays() {
        val entries = mapOf(
            d("2026-10-24") to listOf(entry("past")),
            d("2026-10-31") to listOf(entry("last")),
            d("2026-11-01") to listOf(entry("late")),
        )
        val rows = CalendarViews.agenda(entries, today = d("2026-10-25"))
        assertTrue(rows.contains(AgendaRow.Item(d("2026-10-31"), entry("last"))))
        assertEquals(2, rows.size)
    }

    private fun span(id: String, from: String, to: String) =
        CalendarEntry(EntryKind.EVENT, id, id, null, null, spanStart = d(from), spanEnd = d(to))

    /** Every day [entry] covers gets it, as CalendarOccurrences would. */
    private fun covering(vararg entries: CalendarEntry): Map<LocalDate, List<CalendarEntry>> = entries
        .flatMap { e -> generateSequence(e.spanStart!!) { it.plusDays(1) }.takeWhile { !it.isAfter(e.spanEnd) }.map { it to e } }
        .groupBy({ it.first }, { it.second })

    @Test fun multiDayItemIsListedOnceInTheAgenda() {
        val trip = span("trip", "2026-10-24", "2026-10-27")
        val rows = CalendarViews.agenda(covering(trip), today = d("2026-10-25"))
        assertEquals(listOf(AgendaRow.Heading(d("2026-10-25")), AgendaRow.Item(d("2026-10-25"), trip)), rows)
    }

    @Test fun weekBarsAreClippedToTheWeek() {
        // Week of Monday 26 October 2026.
        val lanes = CalendarViews.weekLanes(d("2026-10-26"), covering(span("trip", "2026-10-24", "2026-10-28")))
        assertEquals(listOf(listOf(SpanSegment(span("trip", "2026-10-24", "2026-10-28"), 0, 2))), lanes)
    }

    @Test fun overlappingBarsGetTheirOwnLanes() {
        val a = span("a", "2026-10-26", "2026-10-28")
        val b = span("b", "2026-10-27", "2026-10-29")
        val c = span("c", "2026-10-30", "2026-11-01")
        val lanes = CalendarViews.weekLanes(d("2026-10-26"), covering(a, b, c))
        assertEquals(listOf(listOf(SpanSegment(a, 0, 2), SpanSegment(c, 4, 6)), listOf(SpanSegment(b, 1, 3))), lanes)
    }

    @Test fun lanesHoldAtMostTheCap() {
        val a = span("a", "2026-10-26", "2026-10-27")
        val c = span("c", "2026-10-30", "2026-10-31")
        val lanes = CalendarViews.weekLanes(d("2026-10-26"), covering(a, c), maxPerLane = 1)
        assertEquals(2, lanes.size)
    }
}
