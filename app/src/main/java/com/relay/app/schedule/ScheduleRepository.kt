package com.relay.app.schedule

import com.relay.app.data.schedule.AfterFire
import com.relay.app.data.schedule.ExceptionKind
import com.relay.app.data.schedule.ScheduleDao
import com.relay.app.data.schedule.ScheduleEntity
import com.relay.app.data.schedule.ScheduleExceptionEntity
import com.relay.app.data.schedule.TargetKind
import com.relay.app.data.settings.AppSettings
import com.relay.app.firing.RingContext
import com.relay.app.firing.RingItem
import com.relay.app.firing.RingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** AlarmManager side, as the repository needs it. Implemented by [AlarmScheduler]; faked in tests. */
interface ScheduleArming {
    /** [rings]: whether this fire rings (vs. quietly starting a target), so the receiver can route it. */
    fun arm(scheduleId: String, triggerAtMillis: Long, isSnooze: Boolean, atMillis: Long = triggerAtMillis, rings: Boolean = true)
    fun cancel(scheduleId: String)
    fun armUpcoming(scheduleId: String, atMillis: Long)
    fun cancelUpcoming(scheduleId: String)
}

/** Notification side, as the repository needs it. Implemented by [FiringNotifications]. */
interface ScheduleNotices {
    suspend fun showUpcoming(s: ScheduleEntity, fireAt: Instant)
    suspend fun showSnoozed(s: ScheduleEntity, until: Instant)
    suspend fun showMissed(s: ScheduleEntity, at: Instant)
    fun clearUpcoming(scheduleId: String)
    fun clearScheduleNotifications(scheduleId: String)
}

/** A row in the Alarms list: the schedule plus its computed next fire time (null = won't ring). */
data class AlarmListItem(val schedule: ScheduleEntity, val nextFire: Instant?, val exceptionCount: Int)

sealed interface FireDecision {
    data class Ring(val item: RingItem) : FireDecision
    /** A timer / relay / stopwatch target: start it quietly. The session is already finished. */
    data class Start(val schedule: ScheduleEntity) : FireDecision
    /** A deleted/disabled schedule, or a duplicate of an occurrence already handled. */
    data object Ignore : FireDecision
}

/**
 * The command layer for schedules (spec §12): the UI, the receivers and the ring service all go
 * through here, so every write re-arms AlarmManager consistently. One mutex serializes all
 * writes, so a fire, a snooze and an edit can't interleave.
 */
class ScheduleRepository(
    private val dao: ScheduleDao,
    private val scheduler: ScheduleArming,
    private val notifications: ScheduleNotices,
    private val settings: suspend () -> AppSettings,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val mutex = Mutex()

    /** Enabled first, sorted by next fire; then the rest by time of day. Calendar reminders aren't alarms. */
    fun observeAlarms(): Flow<List<AlarmListItem>> =
        combine(dao.observeAll(), dao.observeAllExceptions()) { schedules, exceptions ->
            val t = now()
            schedules.filter { it.eventId == null }.map { s ->
                val own = exceptions.filter { it.scheduleId == s.id }
                AlarmListItem(s, if (s.enabled) NextFire.next(s, own, t, zone()) else null, own.size)
            }.sortedWith(
                compareBy<AlarmListItem> { it.nextFire == null }
                    .thenBy { it.nextFire }
                    .thenBy { it.schedule.hour * 60 + it.schedule.minute },
            )
        }

    /** Every schedule with every exception, for the calendar. */
    fun observeWithExceptions(): Flow<Pair<List<ScheduleEntity>, List<ScheduleExceptionEntity>>> =
        combine(dao.observeAll(), dao.observeAllExceptions()) { schedules, exceptions -> schedules to exceptions }

    suspend fun get(id: String): ScheduleEntity? = dao.get(id)
    suspend fun forEvent(eventId: String): List<ScheduleEntity> = dao.forEvent(eventId)
    suspend fun exceptionsFor(id: String): List<ScheduleExceptionEntity> = dao.exceptionsFor(id)
    suspend fun all(): List<ScheduleEntity> = dao.getAll()

    /**
     * Save from an editor. Clears snooze state; a one-off alarm in the past moves to its next
     * date. A calendar reminder keeps its date: it belongs to that day, so a past one just won't fire.
     */
    suspend fun save(schedule: ScheduleEntity): Instant? = mutex.withLock {
        val t = now()
        var s = schedule.copy(snoozedUntilMillis = null, snoozesUsed = 0)
        if (!s.isRepeating && s.eventId == null) s = s.copy(oneOffEpochDay = oneOffDay(s, t))
        dao.upsert(s)
        rescheduleLocked(s.id)
    }

    suspend fun setEnabled(id: String, enabled: Boolean): Instant? = mutex.withLock {
        val s = dao.get(id) ?: return null
        var updated = s.copy(enabled = enabled, snoozedUntilMillis = null, snoozesUsed = 0)
        if (enabled && !s.isRepeating && s.eventId == null) updated = updated.copy(oneOffEpochDay = oneOffDay(updated, now()))
        dao.upsert(updated)
        rescheduleLocked(id)
    }

    /** A deleted calendar's alarms move to [to] (#34). Only the grouping changes; nothing re-arms. */
    suspend fun moveCalendar(from: String, to: String) = mutex.withLock { dao.moveCalendar(from, to) }

    suspend fun delete(id: String) = mutex.withLock {
        scheduler.cancel(id)
        scheduler.cancelUpcoming(id)
        notifications.clearScheduleNotifications(id)
        dao.delete(id)
    }

    /** Adds one exception to each of [scheduleIds] (bulk apply, spec §04). */
    suspend fun addException(
        scheduleIds: Collection<String>, start: LocalDate, end: LocalDate, kind: ExceptionKind, retimeHour: Int?, retimeMinute: Int?,
    ) = mutex.withLock {
        for (id in scheduleIds) {
            dao.upsertException(
                ScheduleExceptionEntity(
                    UUID.randomUUID().toString(), id, minOf(start, end).toEpochDay(), maxOf(start, end).toEpochDay(),
                    kind, retimeHour.takeIf { kind == ExceptionKind.RETIME }, retimeMinute.takeIf { kind == ExceptionKind.RETIME },
                ),
            )
            rescheduleLocked(id)
        }
    }

    suspend fun deleteException(exception: ScheduleExceptionEntity) = mutex.withLock {
        dao.deleteException(exception.id)
        rescheduleLocked(exception.scheduleId)
    }

    /**
     * The fire path. Returns what to ring, and updates state *before* ringing: the next
     * occurrence is armed right away, so a crash mid-ring can't lose tomorrow's alarm.
     */
    suspend fun onFired(id: String, triggerAtMillis: Long, isSnooze: Boolean): FireDecision = mutex.withLock {
        val s = dao.get(id) ?: return FireDecision.Ignore
        if (!s.enabled) return FireDecision.Ignore
        // Already handled this occurrence (e.g. a late fire raced a normal one). Only ignore when
        // the stored state has clearly moved past it; when in doubt, ring.
        if (s.nextFireAtMillis != null && s.nextFireAtMillis > triggerAtMillis) return FireDecision.Ignore

        val updated = if (isSnooze) {
            s.copy(snoozedUntilMillis = null)
        } else {
            s.copy(
                snoozedUntilMillis = null,
                snoozesUsed = 0,
                remainingCount = s.remainingCount?.let { (it - 1).coerceAtLeast(0) },
            )
        }
        dao.upsert(updated.copy(nextFireAtMillis = null))
        notifications.clearScheduleNotifications(id)
        rescheduleLocked(id)

        if (s.target != TargetKind.RING) {
            // Nothing rings, so nothing to dismiss later: end the session now (one-offs delete or
            // switch off here), then hand back what to start.
            finishSessionLocked(id)
            return FireDecision.Start(s)
        }

        val soundUri = s.ring.soundUri ?: settings().defaultAlarmSoundUri
        FireDecision.Ring(
            RingItem(
                source = RingSource.Schedule(s.id),
                context = RingContext.ALARM,
                label = s.label,
                detail = null,
                scheduledAtMillis = triggerAtMillis,
                soundUri = soundUri,
                vibrate = s.ring.vibrate,
                rampSeconds = s.ring.rampSeconds,
                canSnooze = s.ring.maxSnoozes == null || updated.snoozesUsed < s.ring.maxSnoozes,
                snoozeMinutes = s.ring.snoozeMinutes,
            ),
        )
    }

    suspend fun onSnoozed(id: String): Instant? = mutex.withLock {
        val s = dao.get(id) ?: return null
        val until = now().plusSeconds(s.ring.snoozeMinutes * 60L)
        dao.upsert(s.copy(snoozedUntilMillis = until.toEpochMilli(), snoozesUsed = s.snoozesUsed + 1))
        rescheduleLocked(id)
        notifications.showSnoozed(s, until)
        until
    }

    /** Ends a ring session: dismissed, timed out, or a snoozed alarm dismissed early. */
    suspend fun onDismissed(id: String) = mutex.withLock { finishSessionLocked(id) }

    /** "Dismiss now" on the upcoming notification: skip just the next occurrence. */
    suspend fun skipNext(id: String) {
        mutex.withLock {
            val s = dao.get(id) ?: return
            if (!s.isRepeating) {
                finishSessionLocked(id)
                return
            }
            val occurrence = NextFire.nextOccurrence(s, dao.exceptionsFor(id), now(), zone()) ?: return
            // Exceptions are keyed by the occurrence's day, which a lead time can put after the fire's.
            val day = occurrence.plusMinutes(s.leadMinutes.toLong()).toLocalDate().toEpochDay()
            dao.upsertException(ScheduleExceptionEntity(UUID.randomUUID().toString(), id, day, day, ExceptionKind.SKIP, null, null))
            rescheduleLocked(id)
        }
    }

    /**
     * Re-arms every schedule: after boot, a clock or time-zone change, an app update, and on app
     * start (a force-stop clears all alarms). An occurrence missed by less than [LATE_WINDOW_MILLIS]
     * rings now, marked late; an older one only gets a "missed" notification.
     */
    suspend fun rescheduleAll() = mutex.withLock {
        val t = now().toEpochMilli()
        dao.deleteExceptionsEndingBefore(LocalDate.now(zone()).toEpochDay() - 1)
        for (s in dao.getAll()) {
            val missedAt = s.nextFireAtMillis
            if (s.enabled && missedAt != null && missedAt < t - GRACE_MILLIS) {
                if (t - missedAt <= LATE_WINDOW_MILLIS) {
                    // Through the normal fire path, so late fires get the same handling.
                    scheduler.arm(s.id, missedAt, isSnooze = s.snoozedUntilMillis == missedAt, atMillis = t, rings = s.target == TargetKind.RING)
                    continue
                }
                notifications.showMissed(s, Instant.ofEpochMilli(missedAt))
                dao.upsert(s.copy(nextFireAtMillis = null))
                if (!s.isRepeating) {
                    finishSessionLocked(s.id)
                    continue
                }
            }
            rescheduleLocked(s.id)
        }
    }

    private suspend fun finishSessionLocked(id: String) {
        val s = dao.get(id) ?: return
        notifications.clearScheduleNotifications(id)
        val ended = s.copy(snoozedUntilMillis = null, snoozesUsed = 0)
        when {
            !s.isRepeating && s.afterFire == AfterFire.DELETE -> {
                scheduler.cancel(id)
                scheduler.cancelUpcoming(id)
                dao.delete(id)
                return
            }
            !s.isRepeating -> dao.upsert(ended.copy(enabled = false))
            NextFire.nextOccurrence(ended, dao.exceptionsFor(id), now(), zone()) == null ->
                dao.upsert(ended.copy(enabled = false)) // count used up or past its end date
            else -> dao.upsert(ended)
        }
        rescheduleLocked(id)
    }

    /** Arms (or cancels) the fire and the upcoming notification for one schedule. */
    private suspend fun rescheduleLocked(id: String): Instant? {
        val s = dao.get(id) ?: return null
        val t = now()
        // An occurrence that is due but not yet handled by onFired must never be skipped over:
        // re-arm it as is. Otherwise a reschedule racing the fire (the app process starting
        // *because of* the alarm runs rescheduleAll) would advance nextFireAt to tomorrow, and
        // onFired would then take the real fire for a duplicate and stay silent. Only onFired,
        // the missed-alarm path and an editor save clear a pending occurrence.
        val pending = s.nextFireAtMillis
        if (s.enabled && pending != null && pending <= t.toEpochMilli()) {
            scheduler.arm(id, pending, isSnooze = s.snoozedUntilMillis == pending, atMillis = t.toEpochMilli(), rings = s.target == TargetKind.RING)
            return Instant.ofEpochMilli(pending)
        }
        if (!s.enabled) {
            scheduler.cancel(id)
            scheduler.cancelUpcoming(id)
            notifications.clearUpcoming(id)
            if (s.nextFireAtMillis != null) dao.upsert(s.copy(nextFireAtMillis = null))
            return null
        }
        val next = NextFire.next(s, dao.exceptionsFor(id), t, zone())
        if (next == null) {
            scheduler.cancel(id)
            scheduler.cancelUpcoming(id)
            notifications.clearUpcoming(id)
            if (s.nextFireAtMillis != null) dao.upsert(s.copy(nextFireAtMillis = null))
            return null
        }
        val isSnooze = s.snoozedUntilMillis == next.toEpochMilli()
        scheduler.arm(id, next.toEpochMilli(), isSnooze, rings = s.target == TargetKind.RING)
        if (s.nextFireAtMillis != next.toEpochMilli()) dao.upsert(s.copy(nextFireAtMillis = next.toEpochMilli()))

        val lead = settings().upcomingLeadMinutes * 60_000L
        scheduler.cancelUpcoming(id)
        notifications.clearUpcoming(id)
        // A notification target is itself the heads-up: no "upcoming" notice before it.
        if (lead > 0 && !isSnooze && s.target != TargetKind.NOTIFY) {
            val upcomingAt = next.toEpochMilli() - lead
            if (upcomingAt > t.toEpochMilli()) scheduler.armUpcoming(id, upcomingAt)
            else notifications.showUpcoming(s, next)
        }
        return next
    }

    private fun oneOffDay(s: ScheduleEntity, t: Instant): Long {
        val stored = s.oneOffEpochDay
        if (stored != null && NextFire.nextOccurrence(s, emptyList(), t, zone()) != null) return stored
        return NextFire.nextDateFor(s.hour, s.minute, t, zone()).toEpochDay()
    }

    companion object {
        /** A pending fire this close to now is still in flight, not missed. */
        const val GRACE_MILLIS = 2 * 60_000L
        const val LATE_WINDOW_MILLIS = 60 * 60_000L
    }
}
