package com.clockr.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

private const val ACTION_TICK = "com.clockr.app.action.WIDGET_TICK"

/**
 * Widgets can't run their own code between updates, and `ACTION_TIME_TICK` isn't deliverable to
 * a manifest-declared receiver — so a self-rescheduling exact alarm at each minute boundary is
 * the standard way a home-screen clock widget stays live (same shape as AlarmScheduler, just
 * ticking every minute instead of once).
 */
object WidgetTickScheduler {
    fun scheduleNextTick(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val zone = ZoneId.systemDefault()
        val next = LocalDateTime.now(zone).plusMinutes(1).withSecond(0).withNano(0)
        val triggerAt = next.atZone(zone).toInstant().toEpochMilli()
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, tickPendingIntent(context))
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(tickPendingIntent(context))
    }

    private fun tickPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetTickReceiver::class.java).apply { action = ACTION_TICK }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TICK) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Each tick reschedules the next one — that chain has no other way to resume once
                // broken, so a failure updating this tick's view must never skip rescheduling the
                // next one, or the widget silently freezes forever. The two are independent
                // failures on purpose.
                runCatching { ClockrGlanceWidget().updateAll(appContext) }
                runCatching { WidgetTickScheduler.scheduleNextTick(appContext) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
