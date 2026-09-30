package com.relay.app.firing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.relay.app.MainActivity
import com.relay.app.R
import com.relay.app.data.schedule.ScheduleEntity
import com.relay.app.data.settings.SettingsRepository
import com.relay.app.schedule.ScheduleActionReceiver
import com.relay.app.schedule.ScheduleNotices
import com.relay.app.util.TimeText
import java.time.Instant

/**
 * Every notification the alarm side posts. Channels per surface (spec §13) so each can be tuned
 * in system settings. Per-schedule notifications are keyed by tag, so they never collide.
 */
class FiringNotifications(private val context: Context, private val settings: SettingsRepository) : ScheduleNotices {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        manager.createNotificationChannels(
            listOf(
                // Silent: RingPlayer makes the sound. A channel sound would be one-shot and cut off.
                NotificationChannel(CH_RINGING, context.getString(R.string.channel_ringing), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setBypassDnd(true)
                },
                NotificationChannel(CH_UPCOMING, context.getString(R.string.channel_upcoming), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CH_SNOOZED, context.getString(R.string.channel_snoozed), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CH_MISSED, context.getString(R.string.channel_missed), NotificationManager.IMPORTANCE_DEFAULT),
                // Last resort when the ring service can't start: at least the system alarm sound once.
                NotificationChannel(CH_FALLBACK, context.getString(R.string.channel_fallback), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                    )
                    setBypassDnd(true)
                },
            ),
        )
    }

    /**
     * Posted first thing by the service, before any database work, to meet the 5 s rule. On a
     * fire it already carries the full-screen intent: an *update* to a posted notification may
     * not launch it again, so RingActivity starts from this one and waits briefly for the item.
     */
    fun ringingPlaceholder(fullScreen: Boolean): Notification = ringingBuilder(fullScreen)
        .setContentTitle(context.getString(R.string.ring_default_label))
        .build()

    suspend fun ringing(items: List<RingItem>): Notification {
        val top = items.last()
        val is24h = settings.current().clockFormat24h
        val builder = ringingBuilder()
            .setContentTitle(top.label.ifBlank { context.getString(R.string.ring_default_label) })
            .setContentText(
                if (items.size > 1) context.resources.getQuantityString(R.plurals.ring_more_ringing, items.size - 1, TimeText.ofMillis(top.scheduledAtMillis, is24h), items.size - 1)
                else TimeText.ofMillis(top.scheduledAtMillis, is24h),
            )
        if (top.canSnooze) {
            builder.addAction(0, context.getString(R.string.action_snooze), servicePending(RingService.ACTION_SNOOZE, top.scheduleId))
        }
        builder.addAction(0, context.getString(R.string.action_dismiss), servicePending(RingService.ACTION_DISMISS, top.scheduleId))
        return builder.build()
    }

    override suspend fun showUpcoming(s: ScheduleEntity, fireAt: Instant) {
        val is24h = settings.current().clockFormat24h
        val notification = NotificationCompat.Builder(context, CH_UPCOMING)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.upcoming_title, TimeText.of(fireAt, is24h)))
            .setContentText(s.label.ifBlank { null })
            .setContentIntent(openApp())
            .addAction(0, context.getString(R.string.action_dismiss_now), actionPending(ScheduleActionReceiver.ACTION_SKIP_NEXT, s.id))
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(tag(UPCOMING, s.id), 0, notification)
    }

    override suspend fun showSnoozed(s: ScheduleEntity, until: Instant) {
        val is24h = settings.current().clockFormat24h
        val notification = NotificationCompat.Builder(context, CH_SNOOZED)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.snoozed_title, TimeText.of(until, is24h)))
            .setContentText(s.label.ifBlank { null })
            .setContentIntent(openApp())
            .setOngoing(true)
            .addAction(0, context.getString(R.string.action_dismiss), actionPending(ScheduleActionReceiver.ACTION_DISMISS_SNOOZED, s.id))
            .build()
        manager.notify(tag(SNOOZED, s.id), 0, notification)
    }

    override suspend fun showMissed(s: ScheduleEntity, at: Instant) {
        val is24h = settings.current().clockFormat24h
        val notification = NotificationCompat.Builder(context, CH_MISSED)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.missed_title, TimeText.of(at, is24h)))
            .setContentText(s.label.ifBlank { null })
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        manager.notify(tag(MISSED, s.id), 0, notification)
    }

    /** Rang out its timeout without anyone acting on it. */
    suspend fun showTimedOut(item: RingItem) {
        val is24h = settings.current().clockFormat24h
        val notification = NotificationCompat.Builder(context, CH_MISSED)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.timed_out_title, TimeText.ofMillis(item.scheduledAtMillis, is24h)))
            .setContentText(item.label.ifBlank { null })
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        manager.notify(tag(MISSED, item.scheduleId), 0, notification)
    }

    /** Used by FireReceiver if the ring service itself can't be started. */
    fun showFallback(scheduleId: String) {
        val notification = NotificationCompat.Builder(context, CH_FALLBACK)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.ring_default_label))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(openApp())
            .setFullScreenIntent(openApp(), true)
            .setAutoCancel(true)
            .build()
        manager.notify(tag(FALLBACK, scheduleId), 0, notification)
    }

    override fun clearUpcoming(scheduleId: String) = manager.cancel(tag(UPCOMING, scheduleId), 0)

    override fun clearScheduleNotifications(scheduleId: String) {
        listOf(UPCOMING, SNOOZED, MISSED, FALLBACK).forEach { manager.cancel(tag(it, scheduleId), 0) }
    }

    fun canUseFullScreenIntent(): Boolean =
        android.os.Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()

    private fun ringingBuilder(fullScreen: Boolean = true) = NotificationCompat.Builder(context, CH_RINGING)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOngoing(true)
        .setContentIntent(ringActivity())
        .apply { if (fullScreen) setFullScreenIntent(ringActivity(), true) }

    private fun ringActivity(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun servicePending(action: String, scheduleId: String): PendingIntent = PendingIntent.getService(
        context, 0,
        Intent(context, RingService::class.java).setAction(action)
            .setData(Uri.parse("relay://ring/$scheduleId"))
            .putExtra(RingService.EXTRA_SCHEDULE_ID, scheduleId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionPending(action: String, scheduleId: String): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ScheduleActionReceiver::class.java).setAction(action)
            .setData(Uri.parse("relay://schedule/action/$scheduleId"))
            .putExtra(ScheduleActionReceiver.EXTRA_SCHEDULE_ID, scheduleId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun tag(kind: String, scheduleId: String) = "$kind:$scheduleId"

    companion object {
        const val RINGING_NOTIFICATION_ID = 1
        private const val CH_RINGING = "ringing"
        private const val CH_UPCOMING = "upcoming"
        private const val CH_SNOOZED = "snoozed"
        private const val CH_MISSED = "missed"
        private const val CH_FALLBACK = "ringing_fallback"
        private const val UPCOMING = "upcoming"
        private const val SNOOZED = "snoozed"
        private const val MISSED = "missed"
        private const val FALLBACK = "fallback"
    }
}
