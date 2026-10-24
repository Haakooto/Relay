package com.relay.app.event

import com.relay.app.data.event.EventEntity
import com.relay.app.data.event.EventKind
import com.relay.app.data.event.RepeatKind
import com.relay.app.data.schedule.ScheduleEntity
import com.relay.app.data.schedule.ScheduleExceptionEntity
import com.relay.app.data.schedule.TargetKind
import com.relay.app.schedule.NextFire
import com.relay.app.schedule.Recurrence
import com.relay.app.util.MinuteOfDay
import java.time.LocalDate

/** What a row in the calendar stands for. Alarms show only under "Everything scheduled". */
enum class EntryKind { EVENT, REMINDER, ALARM }

/** One row on one day of the calendar (spec §10b). */
data class CalendarEntry(
    val kind: EntryKind,
    /** The event's id, or the alarm's schedule id. */
    val id: String,
    val title: String,
    /** Minutes after midnight; null = all day. */
    val startMinute: Int?,
    val endMinute: Int?,
    /** A multi-day event on a day after its first. */
    val continued: Boolean = false,
    /** The reminder's lead time, null = no reminder. Alarms: always null. */
    val reminderLead: Int? = null,
    /** What the reminder or alarm fires; null = nothing. */
    val target: TargetKind? = null,
)

/**
 * Pure expansion of calendar items, and optionally alarms, into days. The same [Recurrence] rule
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
        }
    }

    /**
     * Every entry from [from] to [to], inclusive, by day. Each day's list has all-day entries
     * first, then by time. Alarms ([showAlarms]) are shown from [today] on only, since what they
     * did in the past isn't recorded here; a count-limited alarm stops after its remaining count.
     */
    fun between(
        events: List<EventEntity>,
        schedules: List<ScheduleEntity>,
        exceptions: List<ScheduleExceptionEntity>,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate,
        showAlarms: Boolean,
    ): Map<LocalDate, List<CalendarEntry>> {
        val reminders = schedules.filter { it.eventId != null }.associateBy { it.eventId }
        val entries = events.flatMap { eventEntries(it, reminders[it.id], from, to) }
        val alarms = if (!showAlarms) emptyList() else schedules.filter { it.eventId == null && it.enabled }.flatMap { s ->
            alarmDays(s, exceptions, today, maxOf(from, today), to).map { (day, minute) ->
                day to CalendarEntry(EntryKind.ALARM, s.id, s.label, minute, null, target = s.target)
            }
        }
        return (entries + alarms).groupBy({ it.first }, { it.second }).mapValues { (_, list) ->
            list.sortedWith(compareBy<CalendarEntry> { it.startMinute != null }.thenBy { it.startMinute }.thenBy { it.title })
        }
    }

    /** One entry per day [event] covers in [from]..[to]; a multi-day one is "continued" after its first day. */
    private fun eventEntries(
        event: EventEntity,
        reminder: ScheduleEntity?,
        from: LocalDate,
        to: LocalDate,
    ): List<Pair<LocalDate, CalendarEntry>> {
        val span = event.spanDays
        val firstStart = maxOf(from.minusDays(span), LocalDate.ofEpochDay(event.startEpochDay))
        return generateSequence(firstStart) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
            .filter { startsOn(event, it) }
            .flatMap { start -> (0..span).asSequence().map { offset -> start.plusDays(offset) to offset } }
            .filter { (day, _) -> !day.isBefore(from) && !day.isAfter(to) }
            .map { (day, offset) ->
                day to CalendarEntry(
                    kind = if (event.kind == EventKind.REMINDER) EntryKind.REMINDER else EntryKind.EVENT,
                    id = event.id,
                    title = event.title,
                    startMinute = if (offset == 0L) event.startMinute else null,
                    endMinute = if (offset == span) event.endMinute else null,
                    continued = offset > 0,
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
