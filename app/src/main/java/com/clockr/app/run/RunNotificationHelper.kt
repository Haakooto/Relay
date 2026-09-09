package com.clockr.app.run

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat

/** Same per-sound-channel approach as [com.clockr.app.alarm.NotificationHelper], for timer/chain runs. */
object RunNotificationHelper {
    const val NOTIFICATION_ID = 9001

    fun runningNotification(context: Context, blockLabel: String, step: RunStep, stepIndex: Int, stepCount: Int) =
        build(context, step.soundProfileId) {
            setContentTitle(blockLabel)
            setContentText(stepLabelText(step, stepIndex, stepCount))
            setUsesChronometer(true)
            setChronometerCountDown(true)
            setWhen(System.currentTimeMillis() + step.seconds * 1000)
            setShowWhen(true)
            addAction(0, "Stop", actionPendingIntent(context, TimerRunService.ACTION_STOP))
        }

    fun awaitingInteractionNotification(context: Context, blockLabel: String, step: RunStep, stepIndex: Int, stepCount: Int) =
        build(context, step.soundProfileId) {
            setContentTitle(blockLabel)
            setContentText("${stepLabelText(step, stepIndex, stepCount)} done — tap Continue")
            addAction(0, "Continue", actionPendingIntent(context, TimerRunService.ACTION_CONTINUE))
            addAction(0, "Stop", actionPendingIntent(context, TimerRunService.ACTION_STOP))
        }

    fun startingNotification(context: Context) =
        build(context, null) {
            setContentTitle("Starting…")
            addAction(0, "Stop", actionPendingIntent(context, TimerRunService.ACTION_STOP))
        }

    fun completionNotification(context: Context, blockLabel: String) =
        build(context, null) {
            setContentTitle(blockLabel)
            setContentText("Finished")
            addAction(0, "Stop", actionPendingIntent(context, TimerRunService.ACTION_COMPLETE_STOP))
            addAction(0, "Restart", actionPendingIntent(context, TimerRunService.ACTION_COMPLETE_RESTART))
            addAction(0, "+1 min", actionPendingIntent(context, TimerRunService.ACTION_COMPLETE_ADD_MINUTE))
        }

    fun cancel(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
    }

    private fun stepLabelText(step: RunStep, stepIndex: Int, stepCount: Int): String {
        val name = step.label ?: "Step ${stepIndex + 1}"
        return if (stepCount > 1) "$name (${stepIndex + 1}/$stepCount)" else name
    }

    private fun build(
        context: Context,
        soundProfileId: String?,
        configure: NotificationCompat.Builder.() -> Unit,
    ): android.app.Notification {
        val channelId = ensureChannelFor(context, soundProfileId)
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .apply(configure)
            .build()
    }

    private fun ensureChannelFor(context: Context, soundProfileId: String?): String {
        val soundUri = soundProfileId?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val channelId = if (soundProfileId.isNullOrBlank()) "timer_run_default" else "timer_run_${soundUri.hashCode()}"

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(channelId) != null) return channelId

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(channelId, "Timers & Chains", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Running timer/chain notifications"
            setSound(soundUri, audioAttributes)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
        return channelId
    }

    private fun actionPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, TimerRunService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
