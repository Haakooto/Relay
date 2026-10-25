package com.relay.app.ui.calendar

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.relay.app.appContainer
import com.relay.app.data.event.CalendarEntity
import com.relay.app.data.event.EventEntity
import com.relay.app.data.event.EventKind
import com.relay.app.data.event.PERSONAL_CALENDAR
import com.relay.app.data.event.RepeatKind
import com.relay.app.data.schedule.RingOptions
import com.relay.app.data.schedule.TargetKind
import com.relay.app.event.ALL_DAY_LEADS
import com.relay.app.event.ReminderDraft
import com.relay.app.event.RepeatDraft
import com.relay.app.event.RepeatFreq
import com.relay.app.event.RepeatOn
import com.relay.app.event.Repeats
import com.relay.app.event.TIMED_LEADS
import com.relay.app.schedule.TargetSpec
import com.relay.app.ui.alarms.TargetEditor
import com.relay.app.util.MinuteOfDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Replaced by the Settings defaults on load; only here so the state is never half-built. */
private val DEFAULT_RING = RingOptions(null, vibrate = true, rampSeconds = 0, snoozeMinutes = 10, maxSnoozes = null)

/** Which picker the editor has open. */
internal enum class Picking { NONE, DATE, START, END_TIME, END_DATE, UNTIL }

data class EventEditState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val id: String = UUID.randomUUID().toString(),
    val kind: EventKind = EventKind.EVENT,
    val title: String = "",
    val note: String = "",
    val date: LocalDate = LocalDate.now(),
    val allDay: Boolean = false,
    /** Minutes after midnight. */
    val startMinute: Int = 0,
    /** Null = no end time. */
    val endMinute: Int? = null,
    /** Null = ends the day it starts. */
    val endDate: LocalDate? = null,
    /** How it repeats (#43). */
    val repeat: RepeatDraft = RepeatDraft(),
    /** Null = no reminder (events only: a reminder always has one). */
    val lead: Int? = 15,
    /** Kept from the stored reminder, or Settings defaults, for a Ring target. */
    val ring: RingOptions = DEFAULT_RING,
    val firstDay: DayOfWeek = DayOfWeek.MONDAY,
    val is24h: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    /** Its calendar (#34), and its own colour (null = the calendar's). */
    val calendarId: String = PERSONAL_CALENDAR,
    val color: Long? = null,
    val calendars: List<CalendarEntity> = emptyList(),
    /** Kept from the stored row: a calendar file's UID (#33), so a re-import still finds it. */
    val icsUid: String? = null,
) {
    /** The lead times offered for this item: a reminder rings at its time, an event picks. */
    val leads: List<Int> get() = when {
        kind == EventKind.REMINDER -> listOf(0)
        allDay -> ALL_DAY_LEADS
        else -> TIMED_LEADS
    }

    /** An end before the start can't be saved. */
    val endsBeforeStart: Boolean get() {
        if (kind == EventKind.REMINDER) return false
        val end = endDate ?: date
        return end.isBefore(date) || (end == date && !allDay && endMinute != null && endMinute <= startMinute)
    }
}

/**
 * Editor for one calendar item (spec §10b). [eventId] null = a new [kind] on [day]. Its reminder
 * uses the same "Fires" section as an alarm, defaulting to a notification.
 */
class EventEditViewModel(
    application: Application,
    private val eventId: String?,
    kind: EventKind,
    day: LocalDate?,
) : AndroidViewModel(application) {
    private val container = application.appContainer
    private val _state = MutableStateFlow(EventEditState(kind = kind))
    val state: StateFlow<EventEditState> = _state.asStateFlow()
    val fires = TargetEditor(container, viewModelScope)

    init {
        viewModelScope.launch {
            val settings = container.settings.current()
            val existing = eventId?.let { container.events.get(it) }
            val reminder = existing?.let { container.events.reminderFor(it.id) }
            val defaults = RingOptions(
                settings.defaultAlarmSoundUri, true, settings.defaultRampSeconds, settings.defaultSnoozeMinutes, null,
            )
            // A new item starts on the next full hour, as most planned things do.
            val nextHour = MinuteOfDay.of(LocalTime.now().truncatedTo(ChronoUnit.HOURS).plusHours(1))
            // A subscribed calendar (#33) is read-only: nothing can be put in it.
            val calendars = container.calendars.all().filter { !it.isSubscribed }
            // A new item goes to the calendar used last, with that calendar's defaults.
            val calendar = calendars.firstOrNull { it.id == settings.lastCalendarId } ?: calendars.firstOrNull()
            val base = withDefaults(
                EventEditState(
                    loaded = true,
                    kind = kind,
                    date = day ?: LocalDate.now(),
                    startMinute = nextHour,
                    lead = if (kind == EventKind.REMINDER) 0 else 15,
                    ring = defaults,
                    firstDay = settings.firstDayOfWeek,
                    is24h = settings.clockFormat24h,
                    calendars = calendars,
                ),
                calendar,
            )
            _state.value = if (existing == null) base else base.copy(
                isNew = false,
                id = existing.id,
                kind = existing.kind,
                title = existing.title,
                note = existing.note,
                date = LocalDate.ofEpochDay(existing.startEpochDay),
                allDay = existing.isAllDay,
                startMinute = existing.startMinute ?: nextHour,
                endMinute = existing.endMinute,
                endDate = existing.endEpochDay?.let(LocalDate::ofEpochDay),
                repeat = Repeats.load(
                    existing.repeatKind, existing.repeatDays, existing.repeatRule,
                    existing.untilEpochDay?.let(LocalDate::ofEpochDay), LocalDate.ofEpochDay(existing.startEpochDay),
                ),
                lead = reminder?.leadMinutes ?: if (existing.kind == EventKind.REMINDER) 0 else null,
                ring = reminder?.ring ?: defaults,
                createdAtMillis = existing.createdAtMillis,
                calendarId = existing.calendarId,
                color = existing.color,
                icsUid = existing.icsUid,
            )
            when {
                reminder != null -> fires.load(reminder.target, reminder.targetDefId, TargetSpec.decode(reminder.targetSpec))
                existing == null -> fires.load(calendar?.defaultTarget ?: TargetKind.NOTIFY, null, TargetSpec())
                else -> fires.load(TargetKind.NOTIFY, null, TargetSpec())
            }
            baseline = _state.value.editable() + fires.editable()
        }
    }

    fun update(transform: (EventEditState) -> EventEditState) = _state.update(transform)

    /** Moving a new item to another calendar also gives it that calendar's defaults. */
    fun setCalendar(id: String) {
        val s = _state.value
        val calendar = s.calendars.firstOrNull { it.id == id } ?: return
        if (!s.isNew) {
            _state.value = s.copy(calendarId = id)
            return
        }
        _state.value = withDefaults(s, calendar)
        fires.setTarget(calendar.defaultTarget)
    }

    /** [s] in [calendar], with its default reminder and repeat. A reminder kind always reminds. */
    private fun withDefaults(s: EventEditState, calendar: CalendarEntity?): EventEditState {
        calendar ?: return s
        val default = calendar.defaultLeadMinutes
        // A lead this kind of item doesn't offer (a 15-minute lead on an all-day item) becomes its first.
        val lead = if (s.kind == EventKind.REMINDER) 0 else default?.let { if (it in s.leads) it else s.leads.first() }
        val repeat = when (calendar.defaultRepeat) {
            RepeatKind.NONE, RepeatKind.CUSTOM -> RepeatDraft()
            RepeatKind.WEEKLY -> RepeatDraft(RepeatFreq.WEEKLY, weekdays = setOf(s.date.dayOfWeek))
            RepeatKind.MONTHLY -> RepeatDraft(RepeatFreq.MONTHLY)
            RepeatKind.YEARLY -> RepeatDraft(RepeatFreq.YEARLY)
        }
        return s.copy(calendarId = calendar.id, lead = lead, repeat = repeat)
    }

    /** All-day changes which leads make sense; keep the pick if it still does. */
    fun setAllDay(allDay: Boolean) = _state.update {
        val next = it.copy(allDay = allDay)
        next.copy(lead = it.lead?.takeIf { lead -> lead in next.leads } ?: it.lead?.let { next.leads.first() })
    }

    /** Weekly starts on the item's own weekday, so it never repeats on nothing. */
    fun setRepeatFreq(freq: RepeatFreq) = updateRepeat {
        val weekdays = if (freq == RepeatFreq.WEEKLY && it.weekdays.isEmpty()) setOf(_state.value.date.dayOfWeek) else it.weekdays
        it.copy(freq = freq, weekdays = weekdays)
    }

    /** Any change to the repeat replaces a rule from a calendar file the editor couldn't show. */
    fun updateRepeat(transform: (RepeatDraft) -> RepeatDraft) = _state.update { it.copy(repeat = transform(it.repeat).copy(custom = null)) }

    private var baseline: List<Any?>? = null
    private fun EventEditState.editable() = listOf(
        title, note, date, allDay, startMinute, endMinute, endDate, repeat, lead, calendarId, color,
    )

    fun isDirty(): Boolean = baseline != null && baseline != _state.value.editable() + fires.editable()

    fun canSave(s: EventEditState): Boolean = !s.endsBeforeStart && (s.lead == null || fires.canSave())

    /** Saves the item and its reminder. [onSaved] gets the reminder's next fire, and whether it has one. */
    fun save(onSaved: (Long?, Boolean) -> Unit) = viewModelScope.launch {
        val s = _state.value
        val f = fires.state.value
        // A date moved to where "the 2nd Tuesday" no longer fits falls back to the date itself.
        val draft = s.repeat.let { if (it.on in Repeats.options(s.date)) it else it.copy(on = RepeatOn.DATE) }
        val repeat = Repeats.store(draft, s.date)
        val event = EventEntity(
            id = s.id,
            kind = s.kind,
            title = s.title.trim(),
            note = s.note.trim(),
            startEpochDay = s.date.toEpochDay(),
            startMinute = if (s.allDay && s.kind == EventKind.EVENT) null else s.startMinute,
            endEpochDay = if (s.kind == EventKind.EVENT) s.endDate?.takeIf { it != s.date }?.toEpochDay() else null,
            endMinute = if (s.kind == EventKind.EVENT && !s.allDay) s.endMinute else null,
            repeatKind = repeat.kind,
            repeatDays = repeat.days,
            untilEpochDay = repeat.until?.toEpochDay(),
            createdAtMillis = s.createdAtMillis,
            calendarId = s.calendarId,
            color = s.color,
            repeatRule = repeat.rule,
            icsUid = s.icsUid,
        )
        container.settings.setLastCalendarId(s.calendarId)
        val reminder = s.lead?.let { lead ->
            ReminderDraft(lead, f.target, fires.storedDefId(f), TargetSpec.encode(f.spec), s.ring)
        }
        val next = container.events.save(event, reminder)
        onSaved(next?.toEpochMilli(), reminder != null)
    }

    /** The saved item as a calendar file, through the share sheet (#33). */
    fun share(context: Context) = viewModelScope.launch {
        container.events.get(_state.value.id)?.let { IcsShare.share(context, container, listOf(it), it.title) }
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        container.events.delete(_state.value.id)
        onDeleted()
    }

    companion object {
        fun factory(application: Application, eventId: String?, kind: EventKind, day: LocalDate?) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = EventEditViewModel(application, eventId, kind, day) as T
        }
    }
}
