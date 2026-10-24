package com.relay.app.run

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.relay.app.MainActivity
import com.relay.app.R
import com.relay.app.data.run.RunPhase
import com.relay.app.firing.RingService
import com.relay.app.firing.RingSource
import com.relay.app.firing.RingState
import com.relay.app.util.DurationText

/**
 * One ongoing notification per run, with a live countdown (the system chronometer, so no
 * service has to tick it) and the run's controls. Silent: sound only ever comes from the
 * firing module.
 */
class RunNotifications(private val context: Context) : RunNotices {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_timers), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override suspend fun show(view: RunView) {
        val run = view.run
        val title = titleFor(context, view)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setSortKey(run.startedAtMillis.toString())
        when (run.phase) {
            RunPhase.RUNNING -> {
                builder.setUsesChronometer(true).setChronometerCountDown(true).setWhen((run.stepEndsAtMillis ?: 0) + view.afterStepMillis)
                    .setShowWhen(true)
                    .setContentText(detailFor(context, view))
                    .addAction(0, context.getString(R.string.run_pause), action(RunActionReceiver.ACTION_PAUSE, run.id))
                    .addAction(0, context.getString(R.string.run_add_minute), action(RunActionReceiver.ACTION_ADD_MINUTE, run.id))
                    .addAction(0, context.getString(R.string.run_stop), action(RunActionReceiver.ACTION_STOP, run.id))
            }
            RunPhase.PAUSED -> {
                builder.setShowWhen(false)
                    .setContentText(context.getString(R.string.run_paused_left, DurationText.clock(((run.remainingMillis ?: 0) + view.afterStepMillis) / 1000)))
                    .addAction(0, context.getString(R.string.run_resume), action(RunActionReceiver.ACTION_RESUME, run.id))
                    .addAction(0, context.getString(R.string.run_add_minute), action(RunActionReceiver.ACTION_ADD_MINUTE, run.id))
                    .addAction(0, context.getString(R.string.run_stop), action(RunActionReceiver.ACTION_STOP, run.id))
            }
            RunPhase.AWAITING -> {
                builder.setShowWhen(false)
                    .setContentText(context.getString(R.string.run_awaiting, detailFor(context, view)))
                    .addAction(0, context.getString(R.string.run_stop_relay), action(RunActionReceiver.ACTION_STOP, run.id))
                    .addAction(0, context.getString(R.string.run_continue), action(RunActionReceiver.ACTION_CONTINUE, run.id))
            }
            RunPhase.DONE -> {
                builder.setShowWhen(false)
                    .setContentText(context.getString(R.string.run_done))
                    .addAction(0, context.getString(R.string.run_stop), action(RunActionReceiver.ACTION_STOP, run.id))
                    .addAction(0, context.getString(R.string.run_restart), action(RunActionReceiver.ACTION_RESTART, run.id))
                    .addAction(0, context.getString(R.string.run_add_minute), action(RunActionReceiver.ACTION_ADD_MINUTE, run.id))
            }
        }
        runCatching { manager.notify(tag(run.id), 0, builder.build()) }
    }

    override fun clear(runId: String) = manager.cancel(tag(runId), 0)

    override fun cancelRing(runId: String) {
        val key = RingSource.Run(runId).key
        if (RingState.items.value.any { it.key == key }) {
            runCatching { context.startService(RingService.cancelIntent(context, key)) }
        }
    }

    private fun action(action: String, runId: String): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, RunActionReceiver::class.java).setAction(action)
            .setData(Uri.parse("relay://run/action/$runId"))
            .putExtra(RunActionReceiver.EXTRA_RUN_ID, runId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun tag(runId: String) = "run:$runId"

    companion object {
        private const val CHANNEL = "timers"

        /** "Pasta", or the kind when unlabelled. */
        fun titleFor(context: Context, view: RunView): String = view.run.label.ifBlank {
            context.getString(if (view.isRelay) R.string.kind_relay else R.string.kind_timer)
        }

        /** "Step 3 of 8 · Work" for relays, the step label (if any) for timers. */
        fun detailFor(context: Context, view: RunView): String {
            val stepLabel = view.step.label
            if (!view.isRelay) return stepLabel?.takeIf { it != view.run.label }.orEmpty()
            val position = context.getString(R.string.run_step_of, view.run.stepIndex + 1, view.plan.size)
            return listOfNotNull(position, stepLabel).joinToString(" · ")
        }
    }
}
