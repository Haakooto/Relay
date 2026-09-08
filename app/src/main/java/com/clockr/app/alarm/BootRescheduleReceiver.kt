package com.clockr.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
                dao.getAllEnabled().forEach { scheduler.schedule(it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
