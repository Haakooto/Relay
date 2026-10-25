package com.relay.app.event

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Six weeks always fit a month, whatever day it starts on. */
const val GRID_DAYS = 42L

/** How far ahead the agenda widget looks, today included. */
const val AGENDA_DAYS = 7L

/** Days in a grid week. */
const val DAYS_PER_WEEK = 7

/** A multi-day item's bar within one week (#35): grid columns [startCol]..[endCol], inclusive. */
data class SpanSegment(val entry: CalendarEntry, val startCol: Int, val endCol: Int)

/** A row of the agenda: a day's heading, or one entry under it. */
sealed interface AgendaRow {
    data class Heading(val day: LocalDate) : AgendaRow
    data class Item(val day: LocalDate, val entry: CalendarEntry) : AgendaRow
}

/** Pure layouts of calendar entries, shared by the Calendar pane and the widgets (spec §10b). */
object CalendarViews {
    /** The first day shown in [month]'s grid: the week start on or before the 1st. */
    fun gridStart(month: YearMonth, firstDay: DayOfWeek): LocalDate =
        month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDay))

    fun gridEnd(month: YearMonth, firstDay: DayOfWeek): LocalDate = gridStart(month, firstDay).plusDays(GRID_DAYS - 1)

    /** The agenda's last day: [AGENDA_DAYS] days from [today], inclusive. */
    fun agendaEnd(today: LocalDate): LocalDate = today.plusDays(AGENDA_DAYS - 1)

    /**
     * Days with entries, in order, each heading followed by its entries. A multi-day item is
     * listed once, on the first of its days in the agenda (#35). Days left empty are left out.
     */
    fun agenda(entries: Map<LocalDate, List<CalendarEntry>>, today: LocalDate): List<AgendaRow> {
        val seen = mutableSetOf<Pair<String, LocalDate?>>()
        return entries.filterKeys { !it.isBefore(today) && !it.isAfter(agendaEnd(today)) }
            .toSortedMap()
            .mapValues { (_, list) -> list.filter { !it.isMultiDay || seen.add(it.id to it.spanStart) } }
            .filterValues { it.isNotEmpty() }
            .flatMap { (day, list) -> listOf(AgendaRow.Heading(day)) + list.map { AgendaRow.Item(day, it) } }
    }

    /**
     * The multi-day bars of the week starting [weekStart], packed into as few lanes as fit:
     * earliest first (longer first on a tie), each in the first lane where it doesn't overlap. [maxPerLane] caps the
     * bars in one lane (a widget row can only hold so many views).
     */
    fun weekLanes(
        weekStart: LocalDate,
        entries: Map<LocalDate, List<CalendarEntry>>,
        maxPerLane: Int = DAYS_PER_WEEK,
    ): List<List<SpanSegment>> {
        val weekEnd = weekStart.plusDays(DAYS_PER_WEEK - 1L)
        val segments = (0 until DAYS_PER_WEEK).flatMap { entries[weekStart.plusDays(it.toLong())].orEmpty() }
            .filter { it.isMultiDay }
            .distinctBy { it.id to it.spanStart }
            .map { e ->
                val start = maxOf(e.spanStart!!, weekStart)
                val end = minOf(e.spanEnd!!, weekEnd)
                SpanSegment(e, ChronoUnit.DAYS.between(weekStart, start).toInt(), ChronoUnit.DAYS.between(weekStart, end).toInt())
            }
            .sortedWith(compareBy<SpanSegment> { it.startCol }.thenByDescending { it.endCol - it.startCol })
        val lanes = mutableListOf<MutableList<SpanSegment>>()
        for (segment in segments) {
            val lane = lanes.firstOrNull { it.size < maxPerLane && it.last().endCol < segment.startCol }
            if (lane != null) lane += segment else lanes += mutableListOf(segment)
        }
        return lanes
    }
}
