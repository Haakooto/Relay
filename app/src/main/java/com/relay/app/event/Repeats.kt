package com.relay.app.event

import com.relay.app.data.event.RepeatKind
import com.relay.app.data.schedule.WeekdayMask
import com.relay.app.schedule.Freq
import com.relay.app.schedule.Rrule
import com.relay.app.schedule.RruleDates
import com.relay.app.schedule.WeekdayNum
import java.time.DayOfWeek
import java.time.LocalDate

/** How often, as the editor offers it (#43). */
enum class RepeatFreq { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

/** Which day a monthly or yearly repeat lands on, worked out from the item's own date. */
enum class RepeatOn { DATE, WEEKDAY, LAST_WEEKDAY, LAST_DAY }

enum class RepeatEnd { NEVER, DATE, COUNT }

/** An item's repeat as the editor holds it. */
data class RepeatDraft(
    val freq: RepeatFreq = RepeatFreq.NONE,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val on: RepeatOn = RepeatOn.DATE,
    val end: RepeatEnd = RepeatEnd.NEVER,
    val until: LocalDate? = null,
    val count: Int = DEFAULT_COUNT,
    /** A rule the editor can't show (from a calendar file): kept as it is until the repeat is changed. */
    val custom: String? = null,
) {
    companion object {
        const val DEFAULT_COUNT = 10
    }
}

/** How a repeat is stored on an item: the simple kinds as before, anything else as a rule. */
data class StoredRepeat(val kind: RepeatKind, val days: Int, val rule: String?, val until: LocalDate?)

/**
 * Between the editor's [RepeatDraft] and what an item stores (#43). Repeats the old fields can
 * hold keep using them, so items that worked before take exactly the same path; the rest become a
 * rule that one evaluator ([RruleDates]) expands for the grid, the widgets and the reminder.
 */
object Repeats {
    fun store(d: RepeatDraft, start: LocalDate): StoredRepeat {
        val until = d.until.takeIf { d.end == RepeatEnd.DATE }
        if (d.freq == RepeatFreq.NONE) return StoredRepeat(RepeatKind.NONE, 0, null, null)
        d.custom?.let { return StoredRepeat(RepeatKind.CUSTOM, 0, it, until) }
        val weekdays = d.weekdays.ifEmpty { setOf(start.dayOfWeek) }
        val simple = d.interval == 1 && d.end != RepeatEnd.COUNT
        return when {
            simple && d.freq == RepeatFreq.WEEKLY -> StoredRepeat(RepeatKind.WEEKLY, WeekdayMask.of(weekdays), null, until)
            simple && d.on == RepeatOn.DATE && d.freq == RepeatFreq.MONTHLY -> StoredRepeat(RepeatKind.MONTHLY, 0, null, until)
            simple && d.on == RepeatOn.DATE && d.freq == RepeatFreq.YEARLY -> StoredRepeat(RepeatKind.YEARLY, 0, null, until)
            else -> StoredRepeat(RepeatKind.CUSTOM, 0, rule(d, start, weekdays).format(), until)
        }
    }

    /** What the editor shows for a stored repeat; a rule it can't express comes back as [RepeatDraft.custom]. */
    fun load(kind: RepeatKind, days: Int, rule: String?, until: LocalDate?, start: LocalDate): RepeatDraft {
        val end = if (until != null) RepeatEnd.DATE else RepeatEnd.NEVER
        return when (kind) {
            RepeatKind.NONE -> RepeatDraft()
            RepeatKind.WEEKLY -> RepeatDraft(RepeatFreq.WEEKLY, weekdays = WeekdayMask.toSet(days), end = end, until = until)
            RepeatKind.MONTHLY -> RepeatDraft(RepeatFreq.MONTHLY, end = end, until = until)
            RepeatKind.YEARLY -> RepeatDraft(RepeatFreq.YEARLY, end = end, until = until)
            RepeatKind.CUSTOM -> {
                val parsed = rule?.let { Rrule.parse(it)?.first } ?: return RepeatDraft()
                val draft = fromRule(parsed, start) ?: return RepeatDraft(freqOf(parsed.freq), custom = rule, end = end, until = until)
                draft.copy(
                    end = if (parsed.count != null) RepeatEnd.COUNT else end,
                    until = until,
                    count = parsed.count ?: RepeatDraft.DEFAULT_COUNT,
                )
            }
        }
    }

    /** The ways a monthly or yearly repeat can land, for an item on [start]. */
    fun options(start: LocalDate): List<RepeatOn> = listOfNotNull(
        RepeatOn.DATE,
        RepeatOn.WEEKDAY.takeIf { ordinal(start) <= MAX_ORDINAL_SHOWN },
        RepeatOn.LAST_WEEKDAY.takeIf { isLastWeekday(start) },
        RepeatOn.LAST_DAY.takeIf { start.dayOfMonth == start.lengthOfMonth() },
    )

    /** Which of its weekday in the month [day] is: 1 to 5. */
    fun ordinal(day: LocalDate): Int = (day.dayOfMonth - 1) / DAYS_IN_WEEK + 1

    fun isLastWeekday(day: LocalDate): Boolean = day.plusWeeks(1).month != day.month

    private fun rule(d: RepeatDraft, start: LocalDate, weekdays: Set<DayOfWeek>): Rrule {
        val count = d.count.takeIf { d.end == RepeatEnd.COUNT }
        val month = if (d.freq == RepeatFreq.YEARLY) listOf(start.monthValue) else emptyList()
        val (byDay, byMonthDay) = when (d.on) {
            RepeatOn.DATE -> emptyList<WeekdayNum>() to listOf(start.dayOfMonth)
            RepeatOn.WEEKDAY -> listOf(WeekdayNum(start.dayOfWeek, ordinal(start))) to emptyList()
            RepeatOn.LAST_WEEKDAY -> listOf(WeekdayNum(start.dayOfWeek, -1)) to emptyList()
            RepeatOn.LAST_DAY -> emptyList<WeekdayNum>() to listOf(-1)
        }
        return when (d.freq) {
            RepeatFreq.NONE, RepeatFreq.DAILY -> Rrule(Freq.DAILY, d.interval, count)
            RepeatFreq.WEEKLY -> {
                val byDay = DayOfWeek.entries.filter { it in weekdays }.map { WeekdayNum(it) }
                Rrule(Freq.WEEKLY, d.interval, count, byDay = byDay)
            }
            RepeatFreq.MONTHLY -> Rrule(Freq.MONTHLY, d.interval, count, byDay, byMonthDay)
            RepeatFreq.YEARLY -> Rrule(Freq.YEARLY, d.interval, count, byDay, byMonthDay, month)
        }
    }

    /** The draft for [r] when the editor can show it as it is, else null. */
    private fun fromRule(r: Rrule, start: LocalDate): RepeatDraft? {
        val base = RepeatDraft(freqOf(r.freq), interval = r.interval)
        val plainDays = r.byDay.all { it.ordinal == null }
        return when (r.freq) {
            Freq.DAILY -> base.takeIf { r.byDay.isEmpty() && r.byMonthDay.isEmpty() && r.byMonth.isEmpty() }
            Freq.WEEKLY -> base.copy(weekdays = r.byDay.map { it.day }.toSet())
                .takeIf { plainDays && r.byMonthDay.isEmpty() && r.byMonth.isEmpty() }
            Freq.MONTHLY -> onFor(r, start)?.let { base.copy(on = it) }?.takeIf { r.byMonth.isEmpty() }
            Freq.YEARLY -> onFor(r, start)?.let { base.copy(on = it) }
                ?.takeIf { r.byMonth.isEmpty() || r.byMonth == listOf(start.monthValue) }
        }
    }

    /** Which [RepeatOn] gives exactly [r]'s day in the month, for an item on [start]. */
    private fun onFor(r: Rrule, start: LocalDate): RepeatOn? {
        val day = r.byDay.singleOrNull()
        return when {
            r.byDay.isEmpty() && (r.byMonthDay.isEmpty() || r.byMonthDay == listOf(start.dayOfMonth)) -> RepeatOn.DATE
            r.byDay.isEmpty() && r.byMonthDay == listOf(-1) && start.dayOfMonth == start.lengthOfMonth() -> RepeatOn.LAST_DAY
            day == null || day.day != start.dayOfWeek || r.byMonthDay.isNotEmpty() -> null
            day.ordinal == ordinal(start) -> RepeatOn.WEEKDAY
            day.ordinal == -1 && isLastWeekday(start) -> RepeatOn.LAST_WEEKDAY
            else -> null
        }
    }

    private fun freqOf(f: Freq) = when (f) {
        Freq.DAILY -> RepeatFreq.DAILY
        Freq.WEEKLY -> RepeatFreq.WEEKLY
        Freq.MONTHLY -> RepeatFreq.MONTHLY
        Freq.YEARLY -> RepeatFreq.YEARLY
    }

    private const val DAYS_IN_WEEK = 7

    /** "The 5th Tuesday" exists in some months only; offer up to the 4th, and "last" for the rest. */
    private const val MAX_ORDINAL_SHOWN = 4
}
