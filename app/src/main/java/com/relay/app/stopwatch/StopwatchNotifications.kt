package com.relay.app.stopwatch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.relay.app.MainActivity
import com.relay.app.R
import com.relay.app.appContainer
import com.relay.app.data.stopwatch.StopwatchEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * An ongoing notification per running stopwatch (spec §07), counting up on the system
 * chronometer, with Lap and Pause. Paused stopwatches have none.
 */
class StopwatchNotifications(private val context: Context) : StopwatchNotices {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        // Default importance, so a running countdown sits with the other notifications instead of
        // folded away under "Silent" (as the v1.0 LOW channel was). The channel itself has no sound
        // or vibration, and only-alert-once keeps the ticking updates quiet. Importance can't be
        // raised on an existing channel, hence the new id.
        manager.deleteNotificationChannel("stopwatch")
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_stopwatch), NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
    }

    override fun show(s: StopwatchEntity, lapCount: Int) {
        val startedAt = s.startedAtMillis ?: return clear(s.id)
        // The chronometer counts from `when`, so put it as far back as the elapsed time.
        val base = startedAt - s.t0Millis - s.accumulatedMillis
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(s.name.ifBlank { context.getString(R.string.pane_stopwatch) })
            .setContentText(if (lapCount > 0) context.resources.getQuantityString(R.plurals.stopwatch_laps, lapCount, lapCount) else null)
            .setUsesChronometer(true)
            .setWhen(base)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .addAction(0, context.getString(R.string.stopwatch_lap), action(StopwatchActionReceiver.ACTION_LAP, s.id))
            .addAction(0, context.getString(R.string.run_pause), action(StopwatchActionReceiver.ACTION_PAUSE, s.id))
            .build()
        runCatching { manager.notify(tag(s.id), 0, notification) }
    }

    override fun clear(id: String) = manager.cancel(tag(id), 0)

    private fun action(action: String, id: String) = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, StopwatchActionReceiver::class.java).setAction(action)
            .setData(Uri.parse("relay://stopwatch/$id")).putExtra(StopwatchActionReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun tag(id: String) = "stopwatch:$id"

    private companion object {
        const val CHANNEL = "stopwatch_running"
    }
}

class StopwatchActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val repo = context.appContainer.stopwatches
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_LAP -> repo.lap(id)
                    ACTION_PAUSE -> repo.pause(id)
                }
            } catch (e: Exception) {
                Log.e("StopwatchActionReceiver", "${intent.action} on $id failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_LAP = "com.relay.app.stopwatch.LAP"
        const val ACTION_PAUSE = "com.relay.app.stopwatch.PAUSE"
        const val EXTRA_ID = "id"
    }
}
