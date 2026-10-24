package com.relay.app.event

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Six weeks always fit a month, whatever day it starts on. */
const val GRID_DAYS = 42L

/** How far ahead the agenda widget looks, today included. */
const val AGENDA_DAYS = 7L

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

    /** Days with entries, in order, each heading followed by its entries. Empty days are left out. */
    fun agenda(entries: Map<LocalDate, List<CalendarEntry>>, today: LocalDate): List<AgendaRow> =
        entries.filterKeys { !it.isBefore(today) && !it.isAfter(agendaEnd(today)) && entries[it].orEmpty().isNotEmpty() }
            .toSortedMap()
            .flatMap { (day, list) -> listOf(AgendaRow.Heading(day)) + list.map { AgendaRow.Item(day, it) } }
}
