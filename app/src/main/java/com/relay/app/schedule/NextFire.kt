package com.relay.app.schedule

import com.relay.app.data.schedule.ExceptionKind
import com.relay.app.data.schedule.ScheduleEntity
import com.relay.app.data.schedule.ScheduleExceptionEntity
import com.relay.app.data.schedule.WeekdayMask
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Which days a repeating schedule or calendar item lands on (spec §04, §10b). One rule for both,
 * so a reminder fires on exactly the days the calendar shows.
 */
object Recurrence {
    /**
     * Monthly when only [monthDay] is set, yearly when [month] is too, else weekly on the
     * [weekdays] mask. A month without [monthDay] (a 31st, a 29 February) has no occurrence.
     */
    fun occursOn(day: LocalDate, weekdays: Int, monthDay: Int?, month: Int?): Boolean = when {
        monthDay != null && month != null -> day.monthValue == month && day.dayOfMonth == monthDay
        monthDay != null -> day.dayOfMonth == monthDay
        else -> WeekdayMask.contains(weekdays, day.dayOfWeek)
    }
}

/**
 * Pure time math for schedules, no Android dependency, so it's unit-tested on the JVM.
 * Kept free of side effects: [ScheduleRepository] decides what to do with the answers.
 */
object NextFire {
    /** Far enough to cross any realistic run of skip exceptions (a long trip). */
    private const val SEARCH_DAYS = 800L

    /** A yearly 29 February can be eight years away (2096 to 2104). */
    private const val YEARLY_SEARCH_DAYS = 8 * 366L

    /**
     * The next time [schedule] should fire strictly after [now], or null if it never will.
     * An active snooze wins over the regular occurrence. Ignores [ScheduleEntity.enabled]: the
     * caller only asks for enabled schedules.
     */
    fun next(schedule: ScheduleEntity, exceptions: List<ScheduleExceptionEntity>, now: Instant, zone: ZoneId): Instant? {
        schedule.snoozedUntilMillis?.let { if (it > now.toEpochMilli()) return Instant.ofEpochMilli(it) }
        return nextOccurrence(schedule, exceptions, now, zone)?.toInstant()
    }

    /** Like [next] but ignoring any snooze: the next regular occurrence. */
    fun nextOccurrence(
        schedule: ScheduleEntity,
        exceptions: List<ScheduleExceptionEntity>,
        now: Instant,
        zone: ZoneId,
    ): ZonedDateTime? {
        val nowZoned = now.atZone(zone)
        if (!schedule.isRepeating) {
            val day = LocalDate.ofEpochDay(schedule.oneOffEpochDay ?: return null)
            val at = fireOn(day, schedule, exceptions, zone) ?: return null
            return at.takeIf { it.isAfter(nowZoned) }
        }
        if (schedule.remainingCount != null && schedule.remainingCount <= 0) return null
        schedule.repeatRule?.let { return nextByRule(schedule, it, exceptions, nowZoned, zone) }

        // A lead time only moves fires earlier, so no occurrence before today can fire after now.
        var day = maxOf(nowZoned.toLocalDate(), schedule.startEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.MIN)
        val searchDays = if (schedule.repeatMonth != null) YEARLY_SEARCH_DAYS else SEARCH_DAYS
        repeat(searchDays.toInt()) {
            if (schedule.endEpochDay != null && day.toEpochDay() > schedule.endEpochDay) return null
            if (Recurrence.occursOn(day, schedule.repeatDays, schedule.repeatMonthDay, schedule.repeatMonth)) {
                val at = fireOn(day, schedule, exceptions, zone)
                if (at != null && at.isAfter(nowZoned)) return at
            }
            day = day.plusDays(1)
        }
        return null
    }

    /**
     * A reminder with a repeat rule (#43): the first of the rule's dates whose fire (less the lead)
     * is after now. The rule is walked from its start, as COUNT is counted from there.
     */
    private fun nextByRule(
        schedule: ScheduleEntity,
        text: String,
        exceptions: List<ScheduleExceptionEntity>,
        nowZoned: ZonedDateTime,
        zone: ZoneId,
    ): ZonedDateTime? {
        val rule = Rrule.parse(text)?.first ?: return null
        val start = LocalDate.ofEpochDay(schedule.startEpochDay ?: return null)
        val until = schedule.endEpochDay?.let(LocalDate::ofEpochDay)
        val today = nowZoned.toLocalDate()
        return RruleDates.dates(rule, start, until, today.plusDays(YEARLY_SEARCH_DAYS))
            .filter { !it.isBefore(today) }
            .mapNotNull { fireOn(it, schedule, exceptions, zone) }
            .firstOrNull { it.isAfter(nowZoned) }
    }

    /** When the occurrence on [day] fires: its time less the lead, or null if an exception skips it. */
    private fun fireOn(day: LocalDate, schedule: ScheduleEntity, exceptions: List<ScheduleExceptionEntity>, zone: ZoneId): ZonedDateTime? {
        val time = timeOn(day, schedule, exceptions) ?: return null
        return ZonedDateTime.of(day, time, zone).minusMinutes(schedule.leadMinutes.toLong())
    }

    /**
     * The time [schedule] fires on [day], or null if an exception skips it. When exceptions
     * overlap, a skip wins over a retime, and the earliest-starting retime wins over later ones.
     */
    fun timeOn(day: LocalDate, schedule: ScheduleEntity, exceptions: List<ScheduleExceptionEntity>): LocalTime? {
        val epochDay = day.toEpochDay()
        val covering = exceptions.filter { it.scheduleId == schedule.id && epochDay in it.startEpochDay..it.endEpochDay }
        if (covering.any { it.kind == ExceptionKind.SKIP }) return null
        val retime = covering.filter { it.kind == ExceptionKind.RETIME && it.retimeHour != null && it.retimeMinute != null }
            .minByOrNull { it.startEpochDay }
        return if (retime != null) LocalTime.of(retime.retimeHour!!, retime.retimeMinute!!)
        else LocalTime.of(schedule.hour, schedule.minute)
    }

    /** The date a new one-off should use: today if [hour]:[minute] is still ahead, else tomorrow. */
    fun nextDateFor(hour: Int, minute: Int, now: Instant, zone: ZoneId): LocalDate {
        val nowZoned = now.atZone(zone)
        val today = ZonedDateTime.of(nowZoned.toLocalDate(), LocalTime.of(hour, minute), zone)
        return if (today.isAfter(nowZoned)) nowZoned.toLocalDate() else nowZoned.toLocalDate().plusDays(1)
    }
}
