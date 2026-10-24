package com.relay.app.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.relay.app.appContainer
import com.relay.app.event.CalendarEntry
import com.relay.app.event.CalendarOccurrences
import com.relay.app.event.CalendarViews
import com.relay.app.event.GRID_DAYS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** The month on show, as six full weeks from [gridStart], with each day's entries. */
data class CalendarState(
    val month: YearMonth,
    val selected: LocalDate,
    val today: LocalDate,
    val gridStart: LocalDate,
    val firstDay: DayOfWeek,
    val is24h: Boolean,
    val showAll: Boolean,
    val entries: Map<LocalDate, List<CalendarEntry>>,
)

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer
    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow(LocalDate.now())

    val state: StateFlow<CalendarState?> = combine(
        container.events.observeAll(),
        container.schedules.observeWithExceptions(),
        container.settings.settings,
        month,
        selected,
    ) { events, (schedules, exceptions), settings, m, sel ->
        val today = LocalDate.now()
        val gridStart = CalendarViews.gridStart(m, settings.firstDayOfWeek)
        CalendarState(
            month = m,
            selected = sel,
            today = today,
            gridStart = gridStart,
            firstDay = settings.firstDayOfWeek,
            is24h = settings.clockFormat24h,
            showAll = settings.calendarShowAll,
            entries = CalendarOccurrences.between(
                events, schedules, exceptions, gridStart, gridStart.plusDays(GRID_DAYS - 1), today, settings.calendarShowAll,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), null)

    init {
        // A widget asked to show a day (spec §10b): take it once.
        viewModelScope.launch {
            container.calendarJump.filterNotNull().collect { day ->
                select(day)
                container.calendarJump.value = null
            }
        }
    }

    fun showMonth(m: YearMonth) { month.value = m }

    /** From the month picker: that month, on today if it's in it, else on its first day. */
    fun jumpTo(m: YearMonth) {
        val today = LocalDate.now()
        select(if (YearMonth.from(today) == m) today else m.atDay(1))
    }

    /** Picking a day from a neighbouring month's edge also turns to that month. */
    fun select(day: LocalDate) {
        selected.value = day
        month.value = YearMonth.from(day)
    }

    fun setShowAll(value: Boolean) = viewModelScope.launch { container.settings.setCalendarShowAll(value) }
}
