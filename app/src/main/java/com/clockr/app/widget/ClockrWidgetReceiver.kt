package com.clockr.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.clockr.app.data.ClockrDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClockrWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockrGlanceWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetTickScheduler.scheduleNextTick(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetTickScheduler.cancel(context)
    }

    /** Explicit cleanup, no FK — matches this codebase's convention (e.g. AlarmException cleanup
     *  on alarm delete) — so a removed widget doesn't leave an orphaned clock_configs row behind. */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val dao = ClockrDatabase.get(context).clockConfigDao()
        CoroutineScope(Dispatchers.IO).launch {
            appWidgetIds.forEach { runCatching { dao.deleteByAppWidgetId(it) } }
        }
    }
}
