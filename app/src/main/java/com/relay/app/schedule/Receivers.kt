package com.relay.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.relay.app.appContainer
import com.relay.app.firing.RingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
private fun BroadcastReceiver.launchAsync(tag: String, block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e(tag, "failed", e)
        } finally {
            pending.finish()
        }
    }
}

/**
 * AlarmManager's fire target. Does as little as possible: hand over to [RingService]. An alarm
 * broadcast from setAlarmClock may start a foreground service from the background.
 */
class FireReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.data?.lastPathSegment ?: return
        val service = RingService.fireIntent(context, scheduleId)
            .putExtra(RingService.EXTRA_TRIGGER_AT, intent.getLongExtra(EXTRA_TRIGGER_AT, System.currentTimeMillis()))
            .putExtra(RingService.EXTRA_IS_SNOOZE, intent.getBooleanExtra(EXTRA_IS_SNOOZE, false))
        try {
            context.startForegroundService(service)
        } catch (e: Exception) {
            // Should never happen for an alarm-clock broadcast. If it does, make *some* noise.
            Log.e(TAG, "can't start RingService, posting fallback notification", e)
            context.appContainer.firingNotifications.showFallback(scheduleId)
        }
    }

    companion object {
        const val EXTRA_TRIGGER_AT = "triggerAt"
        const val EXTRA_IS_SNOOZE = "isSnooze"
        private const val TAG = "FireReceiver"
    }
}

/** Notification actions and the upcoming-alarm timer. */
class ScheduleActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID) ?: return
        val container = context.appContainer
        launchAsync(TAG) {
            when (intent.action) {
                ACTION_SHOW_UPCOMING -> {
                    val s = container.schedules.get(scheduleId) ?: return@launchAsync
                    val next = s.nextFireAtMillis ?: return@launchAsync
                    if (s.enabled && s.snoozedUntilMillis == null) {
                        container.firingNotifications.showUpcoming(s, java.time.Instant.ofEpochMilli(next))
                    }
                }
                ACTION_SKIP_NEXT -> container.schedules.skipNext(scheduleId)
                ACTION_DISMISS_SNOOZED -> container.schedules.onDismissed(scheduleId)
            }
        }
    }

    companion object {
        const val ACTION_SHOW_UPCOMING = "com.relay.app.schedule.SHOW_UPCOMING"
        const val ACTION_SKIP_NEXT = "com.relay.app.schedule.SKIP_NEXT"
        const val ACTION_DISMISS_SNOOZED = "com.relay.app.schedule.DISMISS_SNOOZED"
        const val EXTRA_SCHEDULE_ID = "scheduleId"
        private const val TAG = "ScheduleActionReceiver"
    }
}

/**
 * AlarmManager forgets everything on reboot, and a clock or time-zone change moves wall-clock
 * times. Re-arm all schedules in each case, catching up on anything missed meanwhile.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                val container = context.appContainer
                launchAsync("RescheduleReceiver") {
                    container.schedules.rescheduleAll()
                    container.runs.rearmAll()
                    container.stopwatches.republishAll()
                    // A clock or zone change moves every widget's time; a reboot loses the tick.
                    com.relay.app.widget.ClockWidget.refreshAll(context)
                    com.relay.app.widget.WidgetTicker.schedule(context)
                }
            }
        }
    }
}
