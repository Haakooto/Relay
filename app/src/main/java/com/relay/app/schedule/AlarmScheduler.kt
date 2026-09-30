package com.relay.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.relay.app.MainActivity

/**
 * Thin wrapper over AlarmManager. Knows nothing about schedules beyond an id and a time.
 * Each schedule has at most two pending alarms, told apart by their data URI (which is part of
 * PendingIntent identity): the fire itself, and the upcoming-alarm notification before it.
 */
// Lint's MissingPermission here expects SCHEDULE_EXACT_ALARM; USE_EXACT_ALARM (declared in the
// manifest, granted at install on API 33+) covers both calls.
@android.annotation.SuppressLint("MissingPermission")
class AlarmScheduler(private val context: Context) : ScheduleArming {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Arms the fire for [scheduleId]. [triggerAtMillis] is the occurrence it represents (the
     * idempotency key, see [ScheduleRepository.onFired]); [atMillis] is when to actually fire,
     * which is only earlier-than-now for a late fire after boot.
     * setAlarmClock: the most Doze-proof API, and it shows the system next-alarm indicator.
     */
    override fun arm(scheduleId: String, triggerAtMillis: Long, isSnooze: Boolean, atMillis: Long) {
        val fire = PendingIntent.getBroadcast(
            context, 0,
            fireIntent(scheduleId)
                .putExtra(FireReceiver.EXTRA_TRIGGER_AT, triggerAtMillis)
                .putExtra(FireReceiver.EXTRA_IS_SNOOZE, isSnooze),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), fire)
    }

    override fun cancel(scheduleId: String) {
        PendingIntent.getBroadcast(
            context, 0, fireIntent(scheduleId), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { alarmManager.cancel(it); it.cancel() }
    }

    /** Exact but not an alarm clock: this only posts a notification. */
    override fun armUpcoming(scheduleId: String, atMillis: Long) {
        val pending = PendingIntent.getBroadcast(
            context, 0, upcomingIntent(scheduleId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
    }

    override fun cancelUpcoming(scheduleId: String) {
        PendingIntent.getBroadcast(
            context, 0, upcomingIntent(scheduleId), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { alarmManager.cancel(it); it.cancel() }
    }

    fun canScheduleExact(): Boolean = alarmManager.canScheduleExactAlarms()

    private fun fireIntent(scheduleId: String) =
        Intent(context, FireReceiver::class.java).setData(Uri.parse("relay://schedule/fire/$scheduleId"))

    private fun upcomingIntent(scheduleId: String) =
        Intent(context, ScheduleActionReceiver::class.java)
            .setAction(ScheduleActionReceiver.ACTION_SHOW_UPCOMING)
            .setData(Uri.parse("relay://schedule/upcoming/$scheduleId"))
            .putExtra(ScheduleActionReceiver.EXTRA_SCHEDULE_ID, scheduleId)
}
