package com.relay.app.widget.calendar

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.relay.app.RelayApplication
import com.relay.app.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Midnight for the calendar widgets: one exact, non-wakeup alarm a day so "Today" moves on. Non-
 * wakeup costs nothing while the screen is off; it's delivered when the phone wakes. Stops when
 * no calendar widget is placed.
 */
@SuppressLint("MissingPermission") // USE_EXACT_ALARM, see AlarmScheduler.
object CalendarWidgetTicker {
    suspend fun schedule(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0, Intent(context, CalendarWidgetTickReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (!CalendarWidgets.anyPlaced(context)) {
            alarm.cancel(pending)
            return
        }
        val zone = ZoneId.systemDefault()
        val midnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        alarm.setExact(AlarmManager.RTC, midnight, pending)
    }
}

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
private fun BroadcastReceiver.launchAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        runCatching { block() }.onFailure { Log.e("CalendarWidgets", "refresh failed", it) }
        pending.finish()
    }
}

class CalendarWidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = launchAsync {
        try {
            CalendarWidgets.refreshAll(context)
        } finally {
            // Always re-arm, or "Today" would stick for good after one failed redraw.
            CalendarWidgetTicker.schedule(context)
        }
    }
}

/**
 * Shared by both calendar widgets: arm the midnight tick when placed, drop the settings when
 * removed. Glance's own handlers already hold the broadcast with goAsync (callable once), so this
 * short follow-up work runs in the app's scope instead.
 */
abstract class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        inAppScope(context) {
            CalendarWidgets.refreshAll(context)
            CalendarWidgetTicker.schedule(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val clocks = context.appContainer.clocks
        inAppScope(context) {
            appWidgetIds.forEach { clocks.deleteWidget(it) }
            CalendarWidgetTicker.schedule(context)
        }
    }
}

private fun inAppScope(context: Context, block: suspend () -> Unit) {
    (context.applicationContext as RelayApplication).appScope.launch {
        runCatching { block() }.onFailure { Log.e("CalendarWidgets", "widget update failed", it) }
    }
}

class AgendaWidgetReceiver : CalendarWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}

class MonthWidgetReceiver : CalendarWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}
