package com.relay.app.firing

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.relay.app.appContainer
import com.relay.app.data.run.RunPhase
import com.relay.app.run.RunNotifications
import com.relay.app.schedule.FireDecision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The firing module's engine (spec §13): owns everything that makes noise, for alarms and runs
 * alike. Started as a foreground service; stops itself when nothing is left ringing.
 *
 * Order matters on a fire: foreground first (Android kills a service that doesn't call
 * startForeground within seconds), then the state update (which re-arms what comes next), then
 * sound. If the database is slow or throws, an alarm rings anyway with defaults.
 */
class RingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: RingPlayer
    private var wakeLock: PowerManager.WakeLock? = null
    private val timeouts = mutableMapOf<String, Job>()
    /** Repository calls in flight: the service must not stop (and cancel them) mid-way. */
    private var busy = 0

    override fun onCreate() {
        super.onCreate()
        player = RingPlayer(this, scope)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val ringing = action == ACTION_FIRE || action == ACTION_RUN
        val placeholder = appContainer.firingNotifications.ringingPlaceholder(fullScreen = ringing)
        // The systemExempted type only exists from API 34; Android 13 needs no type at all, and
        // passing an unknown one there would throw.
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(FiringNotifications.RINGING_NOTIFICATION_ID, placeholder, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(FiringNotifications.RINGING_NOTIFICATION_ID, placeholder)
        }
        when (action) {
            ACTION_FIRE -> intent.getStringExtra(EXTRA_SCHEDULE_ID)?.let { id ->
                acquireWakeLock()
                val triggerAt = intent.getLongExtra(EXTRA_TRIGGER_AT, System.currentTimeMillis())
                val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)
                scope.launch { fireSchedule(id, triggerAt, isSnooze) }
            }
            ACTION_RUN -> intent.getStringExtra(EXTRA_RUN_ID)?.let { id ->
                acquireWakeLock()
                scope.launch { ringRun(id) }
            }
            ACTION_ACT -> {
                val key = intent.getStringExtra(EXTRA_KEY)
                val verb = intent.getStringExtra(EXTRA_VERB)?.let { runCatching { RingVerb.valueOf(it) }.getOrNull() }
                if (key != null && verb != null) scope.launch { act(key, verb) }
            }
            ACTION_CANCEL -> intent.getStringExtra(EXTRA_KEY)?.let { key -> scope.launch { silence(key) } }
        }
        if (RingState.items.value.isEmpty() && !ringing) stopIfIdle()
        return START_NOT_STICKY
    }

    private suspend fun fireSchedule(scheduleId: String, triggerAt: Long, isSnooze: Boolean) {
        val decision = try {
            withTimeoutOrNull(DB_TIMEOUT_MILLIS) {
                withContext(Dispatchers.IO) { appContainer.schedules.onFired(scheduleId, triggerAt, isSnooze) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onFired failed, ringing with defaults", e)
            null
        }
        val item = when (decision) {
            is FireDecision.Ring -> decision.item
            FireDecision.Ignore -> { stopIfIdle(); return }
            // Slow or broken database: ring anyway. A silent alarm is the worst outcome.
            null -> RingItem(
                RingSource.Schedule(scheduleId), RingContext.ALARM, "", null, triggerAt, null,
                vibrate = true, rampSeconds = 0, canSnooze = true, snoozeMinutes = 10,
            )
        }
        startRinging(item)
    }

    private suspend fun ringRun(runId: String) {
        val view = runCatching { withContext(Dispatchers.IO) { appContainer.runs.get(runId) } }.getOrNull()
        val ringContext = when (view?.run?.phase) {
            RunPhase.AWAITING -> RingContext.RELAY_BOUNDARY
            RunPhase.DONE -> RingContext.RUN_DONE
            else -> { stopIfIdle(); return } // acted on meanwhile, or stopped
        }
        val step = view.step
        startRinging(
            RingItem(
                source = RingSource.Run(runId),
                context = ringContext,
                label = RunNotifications.titleFor(this, view),
                detail = RunNotifications.detailFor(this, view).ifBlank { null },
                scheduledAtMillis = view.run.alertSinceMillis ?: System.currentTimeMillis(),
                soundUri = step.soundUri,
                vibrate = step.vibrate,
                rampSeconds = 0,
            ),
        )
    }

    private suspend fun startRinging(item: RingItem) {
        RingState.add(item)
        player.play(item)
        updateNotification()
        startTimeout(item)
    }

    /** A button on the ringing screen or notification. */
    private suspend fun act(key: String, verb: RingVerb) {
        val item = RingState.items.value.firstOrNull { it.key == key }
        busy++
        silenceOnly(key)
        val source = item?.source ?: sourceFromKey(key)
        if (source == null) { busy--; stopIfIdle(); return }
        runCatching {
            withContext(Dispatchers.IO) {
                when (source) {
                    is RingSource.Schedule -> when (verb) {
                        RingVerb.SNOOZE -> appContainer.schedules.onSnoozed(source.id)
                        else -> appContainer.schedules.onDismissed(source.id)
                    }
                    is RingSource.Run -> when (verb) {
                        RingVerb.CONTINUE -> appContainer.runs.continueRun(source.id)
                        RingVerb.RESTART -> appContainer.runs.restart(source.id)
                        RingVerb.ADD_MINUTE -> appContainer.runs.addMinute(source.id)
                        else -> appContainer.runs.stop(source.id)
                    }
                }
            }
        }.onFailure { Log.e(TAG, "$verb on $key failed", it) }
        busy--
        stopIfIdle()
    }

    /** Stops the sound for [key] without deciding anything about its source. */
    private suspend fun silence(key: String) {
        silenceOnly(key)
        stopIfIdle()
    }

    private suspend fun silenceOnly(key: String) {
        timeouts.remove(key)?.cancel()
        RingState.remove(key)
        val remaining = RingState.items.value
        if (remaining.isEmpty()) player.stop() else {
            player.play(remaining.last())
            updateNotification()
        }
    }

    private fun startTimeout(item: RingItem) {
        timeouts.remove(item.key)?.cancel()
        timeouts[item.key] = scope.launch {
            val minutes = runCatching { appContainer.settings.current().ringTimeoutMinutes }.getOrDefault(10)
            delay(minutes * 60_000L)
            // Out of the map first: act()/silence() cancel whatever job they find there, which
            // would otherwise be this one, before it records anything.
            timeouts.remove(item.key)
            when (item.source) {
                // An unanswered alarm counts as dismissed, with a notice.
                is RingSource.Schedule -> {
                    runCatching { appContainer.firingNotifications.showTimedOut(item) }
                    act(item.key, RingVerb.DISMISS)
                }
                // A run just goes quiet; it keeps waiting on the Timers pane and its notification.
                is RingSource.Run -> silence(item.key)
            }
        }
    }

    private suspend fun updateNotification() {
        val items = RingState.items.value
        if (items.isEmpty()) return
        runCatching {
            val notification = appContainer.firingNotifications.ringing(items)
            getSystemService(NotificationManager::class.java).notify(FiringNotifications.RINGING_NOTIFICATION_ID, notification)
        }.onFailure { Log.e(TAG, "updating the ringing notification failed", it) }
    }

    private fun stopIfIdle() {
        if (RingState.items.value.isNotEmpty() || timeouts.isNotEmpty() || busy > 0) return
        player.stop()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "relay:ringing")
            .apply { acquire(WAKE_LOCK_MAX_MILLIS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        player.stop()
        releaseWakeLock()
        RingState.clear()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_FIRE = "com.relay.app.ring.FIRE"
        const val ACTION_RUN = "com.relay.app.ring.RUN"
        const val ACTION_ACT = "com.relay.app.ring.ACT"
        const val ACTION_CANCEL = "com.relay.app.ring.CANCEL"
        const val EXTRA_SCHEDULE_ID = "scheduleId"
        const val EXTRA_TRIGGER_AT = "triggerAt"
        const val EXTRA_IS_SNOOZE = "isSnooze"
        const val EXTRA_RUN_ID = "runId"
        const val EXTRA_KEY = "key"
        const val EXTRA_VERB = "verb"
        private const val TAG = "RingService"
        private const val DB_TIMEOUT_MILLIS = 3_000L
        /** Safety cap in case something never releases it; well past any ring timeout. */
        private const val WAKE_LOCK_MAX_MILLIS = 70 * 60_000L

        private fun sourceFromKey(key: String): RingSource? = when {
            key.startsWith("schedule:") -> RingSource.Schedule(key.removePrefix("schedule:"))
            key.startsWith("run:") -> RingSource.Run(key.removePrefix("run:"))
            else -> null
        }

        fun fireIntent(context: Context, scheduleId: String): Intent =
            Intent(context, RingService::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_SCHEDULE_ID, scheduleId)

        fun runIntent(context: Context, runId: String): Intent =
            Intent(context, RingService::class.java).setAction(ACTION_RUN).putExtra(EXTRA_RUN_ID, runId)

        fun actIntent(context: Context, key: String, verb: RingVerb): Intent =
            Intent(context, RingService::class.java).setAction(ACTION_ACT).putExtra(EXTRA_KEY, key).putExtra(EXTRA_VERB, verb.name)

        fun cancelIntent(context: Context, key: String): Intent =
            Intent(context, RingService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_KEY, key)
    }
}
