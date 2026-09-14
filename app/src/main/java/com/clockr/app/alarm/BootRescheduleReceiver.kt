package com.clockr.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.clockr.app.data.ClockrDatabase
import com.clockr.app.widget.ClockrGlanceWidget
import com.clockr.app.widget.WidgetTickScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager alarms don't survive a reboot — re-arm every enabled alarm from the store. The
 * home-screen widget's self-rescheduling tick alarm is exactly the same kind of alarm, and was
 * missing this: previously the tick chain only ever got started once, in `onEnabled` when a
 * widget is first placed, so a widget already on the home screen before a reboot would freeze at
 * whatever time it last showed until removed and re-added.
 */
class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = ClockrDatabase.get(appContext).alarmDao()
                val scheduler = AlarmScheduler(appContext)
                // One bad alarm shouldn't stop the rest from being re-armed after a reboot.
                dao.getAllEnabled().forEach { alarm ->
                    runCatching { scheduler.schedule(alarm) }
                        .onFailure { Log.e("BootReschedule", "Failed to reschedule alarm ${alarm.id}", it) }
                }
                runCatching {
                    val hasWidget = GlanceAppWidgetManager(appContext).getGlanceIds(ClockrGlanceWidget::class.java).isNotEmpty()
                    if (hasWidget) WidgetTickScheduler.scheduleNextTick(appContext)
                }.onFailure { Log.e("BootReschedule", "Failed to reschedule widget tick", it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
