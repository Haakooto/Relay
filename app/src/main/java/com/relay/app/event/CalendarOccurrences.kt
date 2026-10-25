package com.relay.app.event

import com.relay.app.data.event.CalendarEntity
import com.relay.app.data.event.EventEntity
import com.relay.app.data.event.EventKind
import com.relay.app.data.event.RepeatKind
import com.relay.app.data.schedule.ScheduleEntity
import com.relay.app.data.schedule.ScheduleExceptionEntity
import com.relay.app.data.schedule.TargetKind
import com.relay.app.schedule.NextFire
import com.relay.app.schedule.Recurrence
import com.relay.app.schedule.Rrule
import com.relay.app.schedule.RruleDates
import com.relay.app.util.MinuteOfDay
import java.time.LocalDate

/** What a row in the calendar stands for. PHONE: an event from a phone calendar, read-only (#16). */
enum class EntryKind { EVENT, REMINDER, ALARM, PHONE }

/** One row on one day of the calendar (spec §10b). */
data class CalendarEntry(
    val kind: EntryKind,
    /** The event's id, the alarm's schedule id, or a phone event's CalendarContract id. */
    val id: String,
    val title: String,
    /** Minutes after midnight; null = all day. A multi-day item keeps its first day's start here. */
    val startMinute: Int?,
    /** A multi-day item keeps its last day's end here. */
    val endMinute: Int?,
    /** A multi-day item on a day after its first. */
    val continued: Boolean = false,
    /** The reminder's lead time, null = no reminder. Alarms: always null. */
    val reminderLead: Int? = null,
    /** What the reminder or alarm fires; null = nothing. */
    val target: TargetKind? = null,
    val calendarId: String = "",
    /** ARGB: the item's own colour, else its calendar's. */
    val color: Long = FALLBACK_COLOR,
    /** A multi-day item's first and last day (#35); null for a single day. */
    val spanStart: LocalDate? = null,
    val spanEnd: LocalDate? = null,
) {
    val isMultiDay: Boolean get() = spanStart != null
}

/** For an item whose calendar has gone missing; never expected. */
const val FALLBACK_COLOR = 0xFF5C5C5CL

/** Which calendars to show (#34), and every calendar for colours and the alarms default. */
data class CalendarSelection(val calendars: List<CalendarEntity>, val shown: Set<String>) {
    private val byId = calendars.associateBy { it.id }
    private val alarmsDefault = calendars.firstOrNull { it.defaultForAlarms }?.id ?: calendars.firstOrNull()?.id

    /** An alarm without a calendar is in the default for alarms. */
    fun alarmCalendar(s: ScheduleEntity): String? = s.calendarId ?: alarmsDefault

    fun isShown(calendarId: String?): Boolean = calendarId != null && calendarId in shown

    fun color(calendarId: String?, own: Long? = null): Long = own ?: calendarId?.let { byId[it]?.color } ?: FALLBACK_COLOR

    companion object {
        /** The calendars ticked in the pane. */
        fun shownInPane(calendars: List<CalendarEntity>) = CalendarSelection(calendars, calendars.filter { it.shown }.map { it.id }.toSet())
    }
}

/**
 * Pure expansion of calendar items and alarms, in the selected calendars, into days. The same [Recurrence] rule
 * decides the days as decides when a reminder fires, so the grid and the reminders agree.
 */
object CalendarOccurrences {
    /** Whether [event] has an occurrence starting on [day]. */
    fun startsOn(event: EventEntity, day: LocalDate): Boolean {
        val epochDay = day.toEpochDay()
        if (epochDay < event.startEpochDay) return false
        if (event.repeatKind != RepeatKind.NONE && event.untilEpochDay != null && epochDay > event.untilEpochDay) return false
        val start = LocalDate.ofEpochDay(event.startEpochDay)
        return when (event.repeatKind) {
            RepeatKind.NONE -> epochDay == event.startEpochDay
            RepeatKind.WEEKLY ->
                if (event.repeatDays == 0) epochDay == event.startEpochDay else Recurrence.occursOn(day, event.repeatDays, null, null)
            RepeatKind.MONTHLY -> Recurrence.occursOn(day, 0, start.dayOfMonth, null)
            RepeatKind.YEARLY -> Recurrence.occursOn(day, 0, start.dayOfMonth, start.monthValue)
            RepeatKind.CUSTOM -> ruleStarts(event, day, day).any()
        }
    }

    /**
     * A rule item's occurrence starts from [from] to [to] (#43). A rule that won't parse (it
     * shouldn't: the editor and imports only store ones that do) leaves just the item's own date.
     */
    private fun ruleStarts(event: EventEntity, from: LocalDate, to: LocalDate): Sequence<LocalDate> {
        val start = LocalDate.ofEpochDay(event.startEpochDay)
        val rule = event.repeatRule?.let { Rrule.parse(it)?.first }
            ?: return sequenceOf(start).filter { !it.isBefore(from) && !it.isAfter(to) }
        return RruleDates.dates(rule, start, event.untilEpochDay?.let(LocalDate::ofEpochDay), to).filter { !it.isBefore(from) }
    }

    /**
     * Every entry from [from] to [to], inclusive, by day, in the [selection]'s shown calendars.
     * Each day lists multi-day and all-day entries first, then by time. Alarms are shown from
     * [today] on only, since what they did in the past isn't recorded here; a count-limited alarm
     * stops after its remaining count.
     */
    fun between(
        events: List<EventEntity>,
        schedules: List<ScheduleEntity>,
        exceptions: List<ScheduleExceptionEntity>,
        selection: CalendarSelection,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate,
    ): Map<LocalDate, List<CalendarEntry>> {
        val reminders = schedules.filter { it.eventId != null }.associateBy { it.eventId }
        val entries = events.filter { selection.isShown(it.calendarId) }.flatMap { event ->
            eventEntries(event, reminders[event.id], from, to).map { (day, entry) ->
                day to entry.copy(calendarId = event.calendarId, color = selection.color(event.calendarId, event.color))
            }
        }
        val alarms = schedules.filter { it.eventId == null && it.enabled && selection.isShown(selection.alarmCalendar(it)) }.flatMap { s ->
            val calendarId = selection.alarmCalendar(s)
            alarmDays(s, exceptions, today, maxOf(from, today), to).map { (day, minute) ->
                day to CalendarEntry(
                    EntryKind.ALARM, s.id, s.label, minute, null, target = s.target,
                    calendarId = calendarId.orEmpty(), color = selection.color(calendarId),
                )
            }
        }
        return (entries + alarms).groupBy({ it.first }, { it.second }).mapValues { (_, list) -> list.sortedWith(ENTRY_ORDER) }
    }

    /** Two days-to-entries maps as one, each day in display order (phone events joining Relay's, #16). */
    fun merge(a: Map<LocalDate, List<CalendarEntry>>, b: Map<LocalDate, List<CalendarEntry>>): Map<LocalDate, List<CalendarEntry>> =
        (a.keys + b.keys).associateWith { day -> (a[day].orEmpty() + b[day].orEmpty()).sortedWith(ENTRY_ORDER) }

    /** Multi-day and all-day entries first, then by time, then by title. */
    private val ENTRY_ORDER = compareBy<CalendarEntry> { !it.isMultiDay && it.startMinute != null }
        .thenBy { it.startMinute }
        .thenBy { it.title }

    /** One entry per day [event] covers in [from]..[to]; a multi-day one is "continued" after its first day. */
    private fun eventEntries(
        event: EventEntity,
        reminder: ScheduleEntity?,
        from: LocalDate,
        to: LocalDate,
    ): List<Pair<LocalDate, CalendarEntry>> {
        val span = event.spanDays
        val firstStart = maxOf(from.minusDays(span), LocalDate.ofEpochDay(event.startEpochDay))
        val starts = if (event.repeatKind == RepeatKind.CUSTOM) {
            ruleStarts(event, firstStart, to)
        } else {
            generateSequence(firstStart) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.filter { startsOn(event, it) }
        }
        return starts
            .flatMap { start -> (0..span).asSequence().map { offset -> start.plusDays(offset) to offset } }
            .filter { (day, _) -> !day.isBefore(from) && !day.isAfter(to) }
            .map { (day, offset) ->
                day to CalendarEntry(
                    kind = if (event.kind == EventKind.REMINDER) EntryKind.REMINDER else EntryKind.EVENT,
                    id = event.id,
                    title = event.title,
                    startMinute = event.startMinute,
                    endMinute = event.endMinute,
                    continued = offset > 0,
                    spanStart = if (span > 0) day.minusDays(offset) else null,
                    spanEnd = if (span > 0) day.minusDays(offset).plusDays(span) else null,
                    reminderLead = reminder?.leadMinutes,
                    target = reminder?.target,
                )
            }.toList()
    }

    /** The days in [from]..[to] an alarm fires on, with its time that day. A count is used up from [today]. */
    private fun alarmDays(
        s: ScheduleEntity,
        exceptions: List<ScheduleExceptionEntity>,
        today: LocalDate,
        from: LocalDate,
        to: LocalDate,
    ): List<Pair<LocalDate, Int>> {
        val own = exceptions.filter { it.scheduleId == s.id }
        if (!s.isRepeating) {
            val day = LocalDate.ofEpochDay(s.oneOffEpochDay ?: return emptyList())
            if (day.isBefore(from) || day.isAfter(to)) return emptyList()
            return listOfNotNull(NextFire.timeOn(day, s, own)?.let { day to MinuteOfDay.of(it) })
        }
        // Every occurrence from today on, so a count is used up in order; then only those on show.
        val occurrences = generateSequence(today) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
            .filter { inRange(s, it) && Recurrence.occursOn(it, s.repeatDays, s.repeatMonthDay, s.repeatMonth) }
            .mapNotNull { day -> NextFire.timeOn(day, s, own)?.let { day to MinuteOfDay.of(it) } }
        val counted = s.remainingCount?.let { occurrences.take(it.coerceAtLeast(0)) } ?: occurrences
        return counted.filter { (day, _) -> !day.isBefore(from) }.toList()
    }

    private fun inRange(s: ScheduleEntity, day: LocalDate): Boolean =
        (s.startEpochDay == null || day.toEpochDay() >= s.startEpochDay) &&
            (s.endEpochDay == null || day.toEpochDay() <= s.endEpochDay)
}
