package com.relay.app.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.relay.app.appContainer
import com.relay.app.data.event.CalendarEntity
import com.relay.app.event.CalendarEntry
import com.relay.app.event.CalendarOccurrences
import com.relay.app.event.CalendarSelection
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
    val calendars: List<CalendarEntity>,
    val entries: Map<LocalDate, List<CalendarEntry>>,
)

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer
    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow(LocalDate.now())

    private val data = combine(
        container.events.observeAll(), container.schedules.observeWithExceptions(), container.calendars.observeAll(),
    ) { events, schedules, calendars -> Triple(events, schedules, calendars) }

    val state: StateFlow<CalendarState?> = combine(data, container.settings.settings, month, selected) { d, settings, m, sel ->
        val (events, schedules, calendars) = d
        val today = LocalDate.now()
        val gridStart = CalendarViews.gridStart(m, settings.firstDayOfWeek)
        CalendarState(
            month = m,
            selected = sel,
            today = today,
            gridStart = gridStart,
            firstDay = settings.firstDayOfWeek,
            is24h = settings.clockFormat24h,
            calendars = calendars,
            entries = CalendarOccurrences.between(
                events, schedules.first, schedules.second, CalendarSelection.shownInPane(calendars),
                gridStart, gridStart.plusDays(GRID_DAYS - 1), today,
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

    fun setShown(id: String, shown: Boolean) = viewModelScope.launch { container.calendars.setShown(id, shown) }

    fun saveCalendar(calendar: CalendarEntity) = viewModelScope.launch { container.calendars.save(calendar) }

    fun deleteCalendar(id: String, moveTo: String) = viewModelScope.launch { container.calendars.delete(id, moveTo) }
}
