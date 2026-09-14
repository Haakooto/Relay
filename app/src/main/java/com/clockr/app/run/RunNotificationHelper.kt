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
import com.clockr.app.R

/**
 * Two channels per surface, not one (spec §09's "dedicated channel per surface" applies *within*
 * a surface too, here): [ensureProgressChannel] is silent — it's what "starting" and "running"
 * post into, since a countdown just beginning has nothing to alert about — and
 * [ensureAlertChannelFor] is the one place sound/vibration ever gets attached, only reached by
 * the two moments actually worth interrupting for: a step awaiting interaction, and completion.
 * Folding both into one shared channel (as before) meant *every* post replayed that channel's
 * alarm sound, including the "Starting…" notification the instant Play was tapped.
 */
object RunNotificationHelper {
    const val NOTIFICATION_ID = 9001
    const val ERROR_NOTIFICATION_ID = 9002

    private const val PROGRESS_CHANNEL_ID = "timer_run_progress"
    private const val ERROR_CHANNEL_ID = "timer_run_errors"

    fun runningNotification(
        context: Context,
        blockLabel: String,
        step: RunStep,
        stepIndex: Int,
        stepCount: Int,
        endAtMillis: Long,
    ) = build(context, ensureProgressChannel(context)) {
        setContentTitle(blockLabel)
        setContentText(stepLabelText(context, step, stepIndex, stepCount))
        setUsesChronometer(true)
        setChronometerCountDown(true)
        setWhen(endAtMillis)
        setShowWhen(true)
        addAction(0, context.getString(R.string.action_stop), actionPendingIntent(context, TimerRunService.ACTION_STOP))
    }

    fun awaitingInteractionNotification(
        context: Context,
        blockLabel: String,
        step: RunStep,
        stepIndex: Int,
        stepCount: Int,
        haptics: Boolean,
    ) = build(context, ensureAlertChannelFor(context, step.soundProfileId, haptics)) {
        setContentTitle(blockLabel)
        setContentText(context.getString(R.string.run_notification_awaiting_interaction, stepLabelText(context, step, stepIndex, stepCount)))
        addAction(0, context.getString(R.string.action_continue), actionPendingIntent(context, TimerRunService.ACTION_CONTINUE))
        addAction(0, context.getString(R.string.action_stop), actionPendingIntent(context, TimerRunService.ACTION_STOP))
    }

    fun startingNotification(context: Context) =
        build(context, ensureProgressChannel(context)) {
            setContentTitle(context.getString(R.string.run_starting_title))
            addAction(0, context.getString(R.string.action_stop), actionPendingIntent(context, TimerRunService.ACTION_STOP))
        }

    fun completionNotification(context: Context, blockLabel: String, soundProfileId: String?, haptics: Boolean) =
        build(context, ensureAlertChannelFor(context, soundProfileId, haptics)) {
            setContentTitle(blockLabel)
            setContentText(context.getString(R.string.run_finished))
            addAction(0, context.getString(R.string.action_stop), actionPendingIntent(context, TimerRunService.ACTION_COMPLETE_STOP))
            addAction(0, context.getString(R.string.action_restart), actionPendingIntent(context, TimerRunService.ACTION_COMPLETE_RESTART))
            addAction(0, context.getString(R.string.run_add_minute), actionPendingIntent(context, TimerRunService.ACTION_COMPLETE_ADD_MINUTE))
        }

    /** Non-ongoing, auto-cancelling — posted when a run can't start at all (see [TimerRunService]). */
    fun errorNotification(context: Context, message: String): android.app.Notification {
        ensureErrorChannel(context)
        return NotificationCompat.Builder(context, ERROR_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.run_error_notification_title))
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
    }

    fun cancel(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
    }

    private fun stepLabelText(context: Context, step: RunStep, stepIndex: Int, stepCount: Int): String {
        val name = step.label ?: context.getString(R.string.run_step_fallback, stepIndex + 1)
        return if (stepCount > 1) context.getString(R.string.run_step_with_count, name, stepIndex + 1, stepCount) else name
    }

    private fun build(
        context: Context,
        channelId: String,
        configure: NotificationCompat.Builder.() -> Unit,
    ): android.app.Notification =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .apply(configure)
            .build()

    private fun ensureProgressChannel(context: Context): String {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(PROGRESS_CHANNEL_ID) == null) {
            val channel = NotificationChannel(PROGRESS_CHANNEL_ID, context.getString(R.string.run_progress_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.run_progress_channel_desc)
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
        return PROGRESS_CHANNEL_ID
    }

    /**
     * One channel per distinct sound, same reasoning as [com.clockr.app.alarm.NotificationHelper]
     * — only reached for a step awaiting interaction or a finished run, so this is the only path
     * that can ever make a Timer/Chain notification actually sound an alert.
     */
    private fun ensureAlertChannelFor(context: Context, soundProfileId: String?, haptics: Boolean): String {
        val soundUri = soundProfileId?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val channelId = if (soundProfileId.isNullOrBlank()) "timer_alert_default" else "timer_alert_${soundUri.hashCode()}"

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(channelId) != null) return channelId

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(channelId, context.getString(R.string.run_alert_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.run_alert_channel_desc)
            setSound(soundUri, audioAttributes)
            enableVibration(haptics)
        }
        manager.createNotificationChannel(channel)
        return channelId
    }

    private fun ensureErrorChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(ERROR_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(ERROR_CHANNEL_ID, context.getString(R.string.run_error_channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = context.getString(R.string.run_error_channel_desc)
                },
            )
        }
    }

    private fun actionPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, TimerRunService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
