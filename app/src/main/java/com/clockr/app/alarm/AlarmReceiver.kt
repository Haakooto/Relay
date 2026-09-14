package com.clockr.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clockr.app.data.AlarmDao
import com.clockr.app.data.AlarmEntity
import com.clockr.app.data.ClockrDatabase
import com.clockr.app.data.OneOffAfterFire
import com.clockr.app.domain.BlockKind
import com.clockr.app.run.TimerRunService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Handles the three things an alarm can do once scheduled: fire, get dismissed, get snoozed.
 * A repeating alarm's next occurrence is scheduled at *fire* time (spec §04's "delete after"
 * bookkeeping happens here too), not at dismiss time — that way it's rescheduled even if the
 * notification itself is swiped away rather than explicitly dismissed.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(AlarmActions.EXTRA_ALARM_ID) ?: return
        val action = intent.action ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context.applicationContext, action, alarmId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(context: Context, action: String, alarmId: String) {
        val dao = ClockrDatabase.get(context).alarmDao()
        val alarm = dao.findById(alarmId) ?: return
        val scheduler = AlarmScheduler(context)

        when (action) {
            AlarmActions.ACTION_FIRE -> {
                val targetKind = if (alarm.hasBlockTarget) runCatching { BlockKind.valueOf(alarm.targetBlockKind!!) }.getOrNull() else null
                if (targetKind != null) {
                    // Handing off to the run engine is the alarm's whole job here — there's no
                    // Dismiss coming the way a plain alarm gets one, so a one-off's after-fire
                    // bookkeeping (delete/mark-done) applies immediately instead of waiting for it.
                    TimerRunService.start(context, alarm.targetBlockId!!, targetKind)
                    if (alarm.isRepeating) advanceRepeatingAfterFire(dao, scheduler, alarm) else applyDismiss(dao, alarm)
                } else {
                    NotificationHelper.showAlarmFiring(context, alarm)
                    if (alarm.isRepeating) advanceRepeatingAfterFire(dao, scheduler, alarm)
                }
            }
            AlarmActions.ACTION_DISMISS -> {
                NotificationHelper.cancel(context, alarm.id)
                applyDismiss(dao, alarm)
            }
            AlarmActions.ACTION_SNOOZE -> {
                NotificationHelper.cancel(context, alarm.id)
                val used = alarm.snoozesUsed + 1
                if (alarm.maxSnoozes == null || used <= alarm.maxSnoozes) {
                    dao.upsert(alarm.copy(snoozesUsed = used))
                    scheduler.scheduleSnooze(alarm)
                } else {
                    applyDismiss(dao, alarm) // out of snoozes — behave like an explicit dismiss
                }
            }
        }
    }

    private suspend fun applyDismiss(dao: AlarmDao, alarm: AlarmEntity) {
        if (!alarm.isRepeating) {
            when (alarm.oneOffAfterFire) {
                OneOffAfterFire.DELETE -> dao.delete(alarm)
                OneOffAfterFire.MARK_DONE -> dao.upsert(alarm.copy(done = true, enabled = false, snoozesUsed = 0))
            }
        } else {
            dao.upsert(alarm.copy(snoozesUsed = 0))
        }
    }

    private suspend fun advanceRepeatingAfterFire(dao: AlarmDao, scheduler: AlarmScheduler, alarm: AlarmEntity) {
        val remaining = alarm.repeatOccurrencesRemaining?.let { it - 1 }
        val untilExceeded = alarm.repeatUntilEpochDay?.let { LocalDate.now().toEpochDay() >= it } ?: false

        if (remaining == 0 || untilExceeded) {
            dao.delete(alarm)
            return
        }
        val updated = alarm.copy(repeatOccurrencesRemaining = remaining)
        dao.upsert(updated)
        scheduler.schedule(updated)
    }
}
