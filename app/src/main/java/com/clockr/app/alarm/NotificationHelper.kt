package com.clockr.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.clockr.app.data.AlarmEntity

/**
 * Spec §09 — a dedicated channel per surface; this one's for alarms specifically.
 *
 * One real gotcha: a notification's sound is a *channel* property from Android 8 onward, not a
 * per-notification one — [NotificationCompat.Builder.setSound] is silently ignored once the
 * channel already exists. Since each alarm can pick its own sound, that means one channel per
 * distinct sound rather than a single shared "alarms" channel, or every alarm would just play
 * whichever sound the channel happened to be created with first.
 */
object NotificationHelper {
    private const val DEFAULT_CHANNEL_ID = "alarms_default"

    /** Called once at app startup so a channel exists before the user ever picks a custom sound. */
    fun ensureChannel(context: Context) {
        ensureChannelFor(context, "")
    }

    fun showAlarmFiring(context: Context, alarm: AlarmEntity) {
        val channelId = ensureChannelFor(context, alarm.soundProfileId)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val dismissIntent = actionPendingIntent(context, alarm, AlarmActions.ACTION_DISMISS)
        val snoozeIntent = actionPendingIntent(context, alarm, AlarmActions.ACTION_SNOOZE)

        val title = alarm.label.ifBlank { "Alarm" }
        val time = "%02d:%02d".format(alarm.hour, alarm.minute)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
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

    /**
     * One channel per distinct sound, identified by the sound's own URI so the same picked
     * ringtone reuses one channel across alarms rather than multiplying them. Uses the ALARM
     * audio stream (`USAGE_ALARM`), not the notification stream — a phone with its notification
     * volume low or silenced but alarm volume up would otherwise ring silently, which reads
     * exactly like "the alarm didn't fire" even though it technically did.
     */
    private fun ensureChannelFor(context: Context, soundProfileId: String): String {
        val soundUri = soundProfileId.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val channelId = if (soundProfileId.isBlank()) DEFAULT_CHANNEL_ID else "alarm_${soundUri.hashCode()}"

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(channelId) != null) return channelId

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(channelId, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alarm firing notifications"
            setSound(soundUri, audioAttributes)
            enableVibration(true)
            setBypassDnd(true)
        }
        manager.createNotificationChannel(channel)
        return channelId
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
