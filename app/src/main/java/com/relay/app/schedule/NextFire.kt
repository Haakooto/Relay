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
 * Pure time math for schedules, no Android dependency, so it's unit-tested on the JVM.
 * Kept free of side effects: [ScheduleRepository] decides what to do with the answers.
 */
object NextFire {
    /** Far enough to cross any realistic run of skip exceptions (a long trip). */
    private const val SEARCH_DAYS = 800L

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
            val time = timeOn(day, schedule, exceptions) ?: return null
            val at = ZonedDateTime.of(day, time, zone)
            return at.takeIf { it.isAfter(nowZoned) }
        }
        if (schedule.remainingCount != null && schedule.remainingCount <= 0) return null

        var day = nowZoned.toLocalDate()
        repeat(SEARCH_DAYS.toInt()) {
            if (schedule.endEpochDay != null && day.toEpochDay() > schedule.endEpochDay) return null
            if (WeekdayMask.contains(schedule.repeatDays, day.dayOfWeek)) {
                val time = timeOn(day, schedule, exceptions)
                if (time != null) {
                    val at = ZonedDateTime.of(day, time, zone)
                    if (at.isAfter(nowZoned)) return at
                }
            }
            day = day.plusDays(1)
        }
        return null
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
