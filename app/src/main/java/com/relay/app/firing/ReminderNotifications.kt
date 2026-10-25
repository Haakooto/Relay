package com.relay.app.firing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.relay.app.MainActivity
import com.relay.app.R
import com.relay.app.data.event.EventDao
import com.relay.app.data.event.EventEntity
import com.relay.app.data.schedule.ScheduleEntity
import com.relay.app.data.settings.SettingsRepository
import com.relay.app.schedule.ReminderPoster
import com.relay.app.ui.Pane
import com.relay.app.util.MinuteOfDay
import com.relay.app.util.RelativeText
import com.relay.app.util.TimeText
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The Notify target (spec §10b): a heads-up notification instead of a ringing screen. Its own
 * channel, so it can be tuned apart from alarms. A calendar reminder shows its item's day, time
 * and note; a plain alarm shows its time.
 */
class ReminderNotifications(
    private val context: Context,
    private val settings: SettingsRepository,
    private val events: EventDao,
) : ReminderPoster {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannel(CH_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    override suspend fun post(s: ScheduleEntity) {
        val is24h = settings.current().clockFormat24h
        val event = s.eventId?.let { events.get(it) }
        val title = (event?.title ?: s.label).ifBlank { context.getString(R.string.reminder_default_title) }
        val text = if (event == null) TimeText.of(LocalTime.of(s.hour, s.minute), is24h) else describe(event, s, is24h)
        val notification = NotificationCompat.Builder(context, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(if (event != null) Pane.CALENDAR else Pane.ALARMS))
            .setAutoCancel(true)
            .build()
        manager.notify("$REMINDER:${s.id}", 0, notification)
    }

    /** "Tomorrow · 10:00–11:00 · Bring the forms". The lead time says which day it's for. */
    private fun describe(event: EventEntity, s: ScheduleEntity, is24h: Boolean): String {
        val day = LocalDateTime.now().plusMinutes(s.leadMinutes.toLong()).toLocalDate()
        val time = event.startMinute?.let { start ->
            val from = TimeText.of(MinuteOfDay.toTime(start), is24h)
            event.endMinute?.let { end -> "$from–${TimeText.of(MinuteOfDay.toTime(end), is24h)}" } ?: from
        } ?: context.getString(R.string.calendar_all_day)
        return listOf(RelativeText.date(context, day, LocalDate.now()), time, event.note.trim())
            .filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun open(pane: Pane): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_PANE, pane.route),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val CH_REMINDERS = "reminders"
        const val REMINDER = "reminder"
    }
}
