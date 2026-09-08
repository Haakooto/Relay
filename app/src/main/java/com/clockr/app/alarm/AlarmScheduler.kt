package com.clockr.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.clockr.app.MainActivity
import com.clockr.app.data.AlarmEntity
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Wraps [AlarmManager.setAlarmClock] rather than the exact-alarm APIs — an alarm-clock app gets
 * exact firing without needing `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` at all (spec §09's
 * permission dance turns out not to apply here), and the system shows the standard "next alarm"
 * status-bar icon for free.
 */
class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: AlarmEntity) {
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
            context, alarm.id.hashCode(), Intent(context, MainActivity::class.java),
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

    /** Next wall-clock instant this alarm should ring, strictly after now. */
    private fun nextTriggerMillis(alarm: AlarmEntity): Long {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var candidate = now.toLocalDate().atTime(alarm.hour, alarm.minute)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)

        if (alarm.isRepeating) {
            val days = alarm.repeatDaySet()
            while (candidate.dayOfWeek !in days) candidate = candidate.plusDays(1)
        }
        return candidate.atZone(zone).toInstant().toEpochMilli()
    }
}
