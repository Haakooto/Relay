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
}
