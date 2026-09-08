package com.clockr.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

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
}
