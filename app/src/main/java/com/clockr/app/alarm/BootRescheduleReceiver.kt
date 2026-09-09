package com.clockr.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.clockr.app.data.ClockrDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** AlarmManager alarms don't survive a reboot — re-arm every enabled alarm from the store. */
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
            } finally {
                pendingResult.finish()
            }
        }
    }
}
