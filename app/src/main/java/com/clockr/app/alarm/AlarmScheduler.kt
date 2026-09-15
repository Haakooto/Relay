package com.clockr.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.clockr.app.MainActivity
import com.clockr.app.data.AlarmEntity
import com.clockr.app.data.ClockrDatabase
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Wraps [AlarmManager.setAlarmClock] rather than the plain exact-alarm APIs — the system shows
 * the standard "next alarm" status-bar icon for free. Corrected assumption: this does **not**
 * exempt the app from `SCHEDULE_EXACT_ALARM` — that's still required (auto-granted on API 31-32,
 * needs a one-time user grant via system settings on 33+, which [MainActivity] prompts for).
 */
class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val exceptionDao = ClockrDatabase.get(context).alarmExceptionDao()

    suspend fun schedule(alarm: AlarmEntity) {
        setAlarmClock(alarm, nextTriggerMillis(alarm))
    }

    fun scheduleSnooze(alarm: AlarmEntity) {
        setAlarmClock(alarm, System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L)
    }

    fun cancel(alarm: AlarmEntity) {
        alarmManager.cancel(firePendingIntent(alarm))
    }

    private fun setAlarmClock(alarm: AlarmEntity, triggerAtMillis: Long) {
        val showIntent = PendingIntent.getActivity(
            context, alarm.id.hashCode(),
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
            firePendingIntent(alarm),
        )
    }

    private fun firePendingIntent(alarm: AlarmEntity): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmActions.ACTION_FIRE
            putExtra(AlarmActions.EXTRA_ALARM_ID, alarm.id)
        }
        return PendingIntent.getBroadcast(
            context, alarm.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Next wall-clock instant this alarm should ring, strictly after now. Schedule exceptions
     * (skip or override the fire time for a date range) are looked up here so every caller gets
     * exception-aware scheduling for free; an alarm with no exception rows produces identical
     * output to before this existed.
     */
    private suspend fun nextTriggerMillis(alarm: AlarmEntity): Long {
        val exceptions = exceptionDao.getForAlarm(alarm.id)
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var candidate = now.toLocalDate().atTime(alarm.hour, alarm.minute)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)

        while (true) {
            if (alarm.isRepeating && candidate.dayOfWeek !in alarm.repeatDaySet()) {
                candidate = candidate.plusDays(1)
                continue
            }
            val epochDay = candidate.toLocalDate().toEpochDay()
            val exception = exceptions.firstOrNull { epochDay in it.startEpochDay..it.endEpochDay }
            if (exception != null && !exception.isOverride) {
                // Skipped day — advance. Bounded even for a one-off alarm: the loop only keeps
                // advancing while inside this exception's finite date range.
                candidate = candidate.plusDays(1)
                continue
            }
            val fireTime = if (exception != null) {
                candidate.toLocalDate().atTime(exception.overrideHour!!, exception.overrideMinute!!)
            } else {
                candidate
            }
            if (!fireTime.isAfter(now)) {
                candidate = candidate.plusDays(1)
                continue
            }
            return fireTime.atZone(zone).toInstant().toEpochMilli()
        }
    }
}
