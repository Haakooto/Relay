package com.clockr.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.clockr.app.data.AlarmEntity

/** Spec §09 — a dedicated channel per surface; this one's for alarms specifically. */
object NotificationHelper {
    const val ALARM_CHANNEL_ID = "alarms"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(ALARM_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            ALARM_CHANNEL_ID,
            "Alarms",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Alarm firing notifications" }
        manager.createNotificationChannel(channel)
    }

    fun showAlarmFiring(context: Context, alarm: AlarmEntity) {
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val dismissIntent = actionPendingIntent(context, alarm, AlarmActions.ACTION_DISMISS)
        val snoozeIntent = actionPendingIntent(context, alarm, AlarmActions.ACTION_SNOOZE)

        val title = alarm.label.ifBlank { "Alarm" }
        val time = "%02d:%02d".format(alarm.hour, alarm.minute)

        val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(time)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, "Dismiss", dismissIntent)
            .addAction(0, "Snooze ${alarm.snoozeMinutes}m", snoozeIntent)
            .build()

        manager.notify(alarm.id.hashCode(), notification)
    }

    fun cancel(context: Context, alarmId: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(alarmId.hashCode())
    }

    private fun actionPendingIntent(context: Context, alarm: AlarmEntity, action: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
            putExtra(AlarmActions.EXTRA_ALARM_ID, alarm.id)
        }
        return PendingIntent.getBroadcast(
            context, (alarm.id + action).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
