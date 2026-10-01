package com.relay.app.widget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.relay.app.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Minute ticks for widgets: one exact, non-wakeup alarm at the next minute boundary. Non-wakeup
 * means no battery cost while the screen is off; the pending tick is delivered as soon as the
 * phone wakes, so the clock is right by the time anyone looks. Stops when no widgets are placed.
 */
@SuppressLint("MissingPermission") // USE_EXACT_ALARM, see AlarmScheduler.
object WidgetTicker {
    fun schedule(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context)
        if (manager.getAppWidgetIds(ComponentName(context, ClockWidgetReceiver::class.java)).isEmpty()) {
            alarm.cancel(pending)
            return
        }
        val now = System.currentTimeMillis()
        alarm.setExact(AlarmManager.RTC, now - now % 60_000 + 60_000, pending)
    }

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, WidgetTickReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                ClockWidget.refreshAll(context)
            } catch (e: Exception) {
                Log.e("WidgetTickReceiver", "refresh failed", e)
            } finally {
                // Always re-arm, even if this redraw failed, or the clock would freeze for good.
                WidgetTicker.schedule(context)
                pending.finish()
            }
        }
    }
}

class ClockWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetTicker.schedule(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val clocks = context.appContainer.clocks
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { clocks.deleteWidget(it) }
                WidgetTicker.schedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
