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
import com.relay.app.event.PhoneCalendar
import com.relay.app.event.PhoneEvents
import com.relay.app.event.PhoneInstance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

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
    /** The phone's calendars (#16), which are ticked in the pane, and whether Relay may read them. */
    val phoneCalendars: List<PhoneCalendar> = emptyList(),
    val shownPhone: Set<Long> = emptySet(),
    val canReadPhone: Boolean = false,
)

/** The phone's calendars as the calendars sheet needs them (#16). */
data class PhoneSheetState(
    val calendars: List<PhoneCalendar>,
    val shown: Set<Long>,
    val canRead: Boolean,
    val canWrite: Boolean,
)

/** What the phone's calendars add to the month on show. */
private data class PhoneData(
    val canRead: Boolean,
    val calendars: List<PhoneCalendar>,
    val shown: Set<Long>,
    val instances: List<PhoneInstance>,
)

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer
    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow(LocalDate.now())

    private val phone = container.phoneCalendars

    private val data = combine(
        container.events.observeAll(), container.schedules.observeWithExceptions(), container.calendars.observeAll(),
    ) { events, schedules, calendars -> Triple(events, schedules, calendars) }

    /** Re-read when a phone calendar changes, access changes, or the month or its ticks do. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val phoneData = combine(phone.changes(), month, container.settings.settings) { _, m, settings -> m to settings }
        .mapLatest { (m, settings) ->
            val calendars = phone.calendars()
            val shown = settings.shownPhoneCalendars intersect calendars.map { it.id }.toSet()
            val start = CalendarViews.gridStart(m, settings.firstDayOfWeek)
            val instances = phone.instances(start, start.plusDays(GRID_DAYS - 1), shown, ZoneId.systemDefault())
            PhoneData(phone.canRead(), calendars, shown, instances)
        }

    val state: StateFlow<CalendarState?> = combine(
        data, container.settings.settings, month, selected, phoneData,
    ) { d, settings, m, sel, p ->
        val (events, schedules, calendars) = d
        val today = LocalDate.now()
        val gridStart = CalendarViews.gridStart(m, settings.firstDayOfWeek)
        val gridEnd = gridStart.plusDays(GRID_DAYS - 1)
        val relay = CalendarOccurrences.between(
            events, schedules.first, schedules.second, CalendarSelection.shownInPane(calendars), gridStart, gridEnd, today,
        )
        // Relay's own copies in a phone calendar would show twice: leave them out.
        val copies = events.mapNotNull { it.providerEventId }.toSet()
        val fromPhone = PhoneEvents.entries(p.instances, copies, gridStart, gridEnd, ZoneId.systemDefault())
        CalendarState(
            month = m,
            selected = sel,
            today = today,
            gridStart = gridStart,
            firstDay = settings.firstDayOfWeek,
            is24h = settings.clockFormat24h,
            calendars = calendars,
            entries = CalendarOccurrences.merge(relay, fromPhone),
            phoneCalendars = p.calendars,
            shownPhone = p.shown,
            canReadPhone = p.canRead,
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

    fun setPhoneShown(id: Long, shown: Boolean) = viewModelScope.launch { container.settings.setPhoneCalendarShown(id, shown) }

    /** After a calendar permission request: read the phone's calendars again. */
    fun accessChanged() = phone.accessChanged()

    fun canWritePhone(): Boolean = phone.canWrite()
}
