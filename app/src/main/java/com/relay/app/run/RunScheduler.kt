package com.relay.app.run

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.relay.app.MainActivity

/**
 * One pending alarm per run, for the current step's end. setAlarmClock, like alarms: it's the
 * only API Doze never defers or rate-limits, and a relay of 1-minute steps with the screen off
 * would otherwise drift by many minutes. Side effect: the status bar shows the alarm icon and
 * the lock screen's "next alarm" can show a timer's end. Accepted for reliability (spec §13).
 */
@SuppressLint("MissingPermission") // USE_EXACT_ALARM, see AlarmScheduler.
class RunScheduler(private val context: Context) : RunArming {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun armStepEnd(runId: String, stepIndex: Int, atMillis: Long) {
        val fire = PendingIntent.getBroadcast(
            context, 0, intent(runId).putExtra(RunStepReceiver.EXTRA_STEP_INDEX, stepIndex),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), fire)
    }

    override fun cancel(runId: String) {
        PendingIntent.getBroadcast(context, 0, intent(runId), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
            ?.let { alarmManager.cancel(it); it.cancel() }
    }

    private fun intent(runId: String) =
        Intent(context, RunStepReceiver::class.java).setData(Uri.parse("relay://run/step/$runId"))
}
