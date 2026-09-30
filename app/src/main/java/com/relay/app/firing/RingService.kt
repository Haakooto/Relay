package com.relay.app.firing

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.relay.app.appContainer
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
 * The firing module's engine (spec §13): owns everything that makes noise. Started by
 * FireReceiver as a foreground service; stops itself when nothing is left ringing.
 *
 * Order matters on a fire: foreground first (Android kills a service that doesn't call
 * startForeground within seconds), then the schedule update (which re-arms the next occurrence),
 * then sound. If the database is slow or throws, it rings anyway with defaults.
 */
class RingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: RingPlayer
    private var wakeLock: PowerManager.WakeLock? = null
    private val timeouts = mutableMapOf<String, Job>()

    override fun onCreate() {
        super.onCreate()
        player = RingPlayer(this, scope)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = appContainer
        val placeholder = container.firingNotifications.ringingPlaceholder(fullScreen = intent?.action == ACTION_FIRE)
        // The systemExempted type only exists from API 34; Android 13 needs no type at all, and
        // passing an unknown one there would throw.
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(FiringNotifications.RINGING_NOTIFICATION_ID, placeholder, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(FiringNotifications.RINGING_NOTIFICATION_ID, placeholder)
        }
        val scheduleId = intent?.getStringExtra(EXTRA_SCHEDULE_ID)
        when (intent?.action) {
            ACTION_FIRE -> if (scheduleId != null) {
                acquireWakeLock()
                val triggerAt = intent.getLongExtra(EXTRA_TRIGGER_AT, System.currentTimeMillis())
                val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)
                scope.launch { fire(scheduleId, triggerAt, isSnooze) }
            }
            ACTION_DISMISS -> if (scheduleId != null) scope.launch { finish(scheduleId, snooze = false) }
            ACTION_SNOOZE -> if (scheduleId != null) scope.launch { finish(scheduleId, snooze = true) }
        }
        if (RingState.items.value.isEmpty() && intent?.action != ACTION_FIRE) stopIfIdle()
        return START_NOT_STICKY
    }

    private suspend fun fire(scheduleId: String, triggerAt: Long, isSnooze: Boolean) {
        val repository = appContainer.schedules
        val decision = try {
            withTimeoutOrNull(DB_TIMEOUT_MILLIS) {
                withContext(Dispatchers.IO) { repository.onFired(scheduleId, triggerAt, isSnooze) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onFired failed, ringing with defaults", e)
            null
        }
        val item = when (decision) {
            is FireDecision.Ring -> decision.item
            FireDecision.Ignore -> { stopIfIdle(); return }
            // Slow or broken database: ring anyway. A silent alarm is the worst outcome.
            null -> RingItem(scheduleId, "", triggerAt, null, vibrate = true, rampSeconds = 0, canSnooze = true, snoozeMinutes = 10)
        }
        RingState.add(item)
        player.play(item)
        updateNotification()
        startTimeout(item)
    }

    private suspend fun finish(scheduleId: String, snooze: Boolean) {
        timeouts.remove(scheduleId)?.cancel()
        val wasRinging = RingState.items.value.any { it.scheduleId == scheduleId }
        RingState.remove(scheduleId)
        afterItemRemoved()
        if (!wasRinging) { stopIfIdle(); return }
        val repository = appContainer.schedules
        runCatching {
            withContext(Dispatchers.IO) {
                if (snooze) repository.onSnoozed(scheduleId) else repository.onDismissed(scheduleId)
            }
        }.onFailure { Log.e(TAG, "finishing $scheduleId failed", it) }
        stopIfIdle()
    }

    private fun startTimeout(item: RingItem) {
        timeouts.remove(item.scheduleId)?.cancel()
        timeouts[item.scheduleId] = scope.launch {
            val minutes = runCatching { appContainer.settings.current().ringTimeoutMinutes }.getOrDefault(10)
            delay(minutes * 60_000L)
            runCatching { appContainer.firingNotifications.showTimedOut(item) }
            // Out of the map first: finish() cancels whatever job it finds there, which would
            // otherwise be this one, before it records the dismissal.
            timeouts.remove(item.scheduleId)
            finish(item.scheduleId, snooze = false)
        }
    }

    private suspend fun afterItemRemoved() {
        val remaining = RingState.items.value
        if (remaining.isEmpty()) player.stop()
        else {
            player.play(remaining.last())
            updateNotification()
        }
    }

    private suspend fun updateNotification() {
        val items = RingState.items.value
        if (items.isEmpty()) return
        runCatching {
            val notification = appContainer.firingNotifications.ringing(items)
            getSystemService(android.app.NotificationManager::class.java)
                .notify(FiringNotifications.RINGING_NOTIFICATION_ID, notification)
        }.onFailure { Log.e(TAG, "updating the ringing notification failed", it) }
    }

    private fun stopIfIdle() {
        if (RingState.items.value.isNotEmpty() || timeouts.isNotEmpty()) return
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
        const val ACTION_DISMISS = "com.relay.app.ring.DISMISS"
        const val ACTION_SNOOZE = "com.relay.app.ring.SNOOZE"
        const val EXTRA_SCHEDULE_ID = "scheduleId"
        const val EXTRA_TRIGGER_AT = "triggerAt"
        const val EXTRA_IS_SNOOZE = "isSnooze"
        private const val TAG = "RingService"
        private const val DB_TIMEOUT_MILLIS = 3_000L
        /** Safety cap in case something never releases it; well past any ring timeout. */
        private const val WAKE_LOCK_MAX_MILLIS = 70 * 60_000L

        fun actionIntent(context: Context, action: String, scheduleId: String): Intent =
            Intent(context, RingService::class.java).setAction(action).putExtra(EXTRA_SCHEDULE_ID, scheduleId)
    }
}
