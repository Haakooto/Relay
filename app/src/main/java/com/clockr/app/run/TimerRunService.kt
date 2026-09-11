package com.clockr.app.run

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.clockr.app.data.BlockRepository
import com.clockr.app.data.ClockrDatabase
import com.clockr.app.data.SettingsRepository
import com.clockr.app.data.TransitionBeepTone
import com.clockr.app.domain.BlockKind
import com.clockr.app.domain.TransitionMode
import com.clockr.chainlang.SafetyLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs exactly one Timer/Chain at a time (v1 scope — Stopwatches are explicitly multi-instance,
 * this isn't yet). No per-second UI tick: the notification uses Android's native chronometer to
 * count down, so the only internal timing is one `delay(stepSeconds)` per step. State doesn't
 * survive the process being killed — a known limitation, not an oversight.
 */
class TimerRunService : Service() {

    companion object {
        const val ACTION_START = "com.clockr.app.run.action.START"
        const val ACTION_STOP = "com.clockr.app.run.action.STOP"
        const val ACTION_CONTINUE = "com.clockr.app.run.action.CONTINUE"
        const val ACTION_COMPLETE_STOP = "com.clockr.app.run.action.COMPLETE_STOP"
        const val ACTION_COMPLETE_RESTART = "com.clockr.app.run.action.COMPLETE_RESTART"
        const val ACTION_COMPLETE_ADD_MINUTE = "com.clockr.app.run.action.COMPLETE_ADD_MINUTE"
        const val EXTRA_BLOCK_ID = "block_id"
        const val EXTRA_BLOCK_KIND = "block_kind"

        fun start(context: Context, blockId: String, kind: BlockKind) {
            val intent = Intent(context, TimerRunService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_BLOCK_ID, blockId)
                putExtra(EXTRA_BLOCK_KIND, kind.name)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var runJob: Job? = null

    private lateinit var db: ClockrDatabase
    private lateinit var repository: BlockRepository
    private lateinit var settingsRepository: SettingsRepository

    private var plan: List<RunStep> = emptyList()
    private var stepIndex = 0
    private var blockLabel = ""
    private var persistent = false
    private var runningBlockId: String? = null
    private var runningBlockKind: BlockKind? = null
    private var hapticsEnabled = true

    override fun onCreate() {
        super.onCreate()
        db = ClockrDatabase.get(applicationContext)
        repository = BlockRepository(db)
        settingsRepository = SettingsRepository(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // startForeground() must be called unconditionally and first: we've already been
                // launched via startForegroundService(), and throwing before reaching this call
                // (e.g. a malformed extra) crashes the service instead of just failing the run.
                startForeground(RunNotificationHelper.NOTIFICATION_ID, RunNotificationHelper.startingNotification(this))
                val blockId = intent.getStringExtra(EXTRA_BLOCK_ID)
                val kind = intent.getStringExtra(EXTRA_BLOCK_KIND)?.let { runCatching { BlockKind.valueOf(it) }.getOrNull() }
                if (blockId == null || kind == null) {
                    abortRun(null)
                } else {
                    beginRun(blockId, kind)
                }
            }
            ACTION_STOP, ACTION_COMPLETE_STOP -> finishRun()
            ACTION_CONTINUE -> {
                stepIndex++
                runFromCurrentStep()
            }
            ACTION_COMPLETE_RESTART -> {
                stepIndex = 0
                runFromCurrentStep()
            }
            ACTION_COMPLETE_ADD_MINUTE -> {
                val last = plan.lastIndex
                if (last >= 0) {
                    plan = plan.toMutableList().also { it[last] = it[last].copy(seconds = it[last].seconds + 60) }
                }
                runFromCurrentStep()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Resolving/building a plan can fail well after `startForeground()` already ran — an
     * `UnknownReferenceException` from a dangling label (see [finishRun]), an oversized chain,
     * whatever. None of that may crash the process; it just means this particular run can't
     * start.
     */
    private fun beginRun(blockId: String, kind: BlockKind) = scope.launch {
        runJob?.cancel()
        runningBlockId = blockId
        runningBlockKind = kind
        try {
            val settings = settingsRepository.current()
            hapticsEnabled = settings.haptics
            val limits = SafetyLimits(maxPlanSize = settings.maxExecutionPlanSize)
            when (kind) {
                BlockKind.TIMER -> {
                    val timer = db.timerDefDao().findById(blockId)
                    if (timer == null) { abortRun("This Timer no longer exists"); return@launch }
                    blockLabel = timer.label.ifBlank { "Timer" }
                    persistent = timer.persistent
                    plan = RunPlanBuilder(db, repository).buildForTimer(timer)
                }
                BlockKind.CHAIN -> {
                    val chain = db.chainDefDao().findById(blockId)
                    if (chain == null) { abortRun("This Chain no longer exists"); return@launch }
                    blockLabel = chain.label.ifBlank { "Chain" }
                    persistent = chain.persistent
                    plan = RunPlanBuilder(db, repository).buildForChain(chain, limits)
                }
            }
            if (plan.isEmpty()) { abortRun(null); return@launch }
            stepIndex = 0
            runFromCurrentStep()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val label = blockLabel.ifBlank { "this run" }
            abortRun("Couldn't start $label: ${e.message ?: e::class.simpleName}")
        }
    }

    private fun runFromCurrentStep() {
        runJob?.cancel()
        runJob = scope.launch {
            while (stepIndex < plan.size) {
                val step = plan[stepIndex]
                postRunning(step)
                delay(step.seconds * 1000)

                val isLast = stepIndex == plan.lastIndex
                if (isLast) {
                    postCompletion()
                    return@launch
                }
                if (step.transitionMode == TransitionMode.SEAMLESS) {
                    playTransitionBeep(step.transitionBeepTone)
                    stepIndex++
                    continue
                } else {
                    postAwaitingInteraction(step)
                    return@launch
                }
            }
        }
    }

    /**
     * Seamless transitions deliberately skip the alert notification (no "awaiting interaction"
     * stop), which left them completely silent — with the step's own picked sound reserved for
     * completion/interaction alerts (often long, and a bad fit for back-to-back short steps that
     * would overlap), a step boundary needs its own cue independent of any picked ringtone. See
     * [TransitionBeep] — the tone is set per-Chain (its config screen), not globally; "Silent"
     * turns it back off for that one chain.
     */
    private fun playTransitionBeep(tone: TransitionBeepTone) {
        scope.launch(Dispatchers.Default) { TransitionBeep.play(tone) }
    }

    private fun postRunning(step: RunStep) {
        val endAtMillis = System.currentTimeMillis() + step.seconds * 1000
        ActiveRunState.update(ActiveRunSnapshot(blockLabel, step.label, stepIndex, plan.size, step.seconds, RunPhase.RUNNING, endAtMillis))
        val notification = RunNotificationHelper.runningNotification(this, blockLabel, step, stepIndex, plan.size, endAtMillis)
        startForeground(RunNotificationHelper.NOTIFICATION_ID, notification)
    }

    private fun postAwaitingInteraction(step: RunStep) {
        ActiveRunState.update(ActiveRunSnapshot(blockLabel, step.label, stepIndex, plan.size, 0, RunPhase.AWAITING_INTERACTION))
        val notification = RunNotificationHelper.awaitingInteractionNotification(this, blockLabel, step, stepIndex, plan.size, hapticsEnabled)
        startForeground(RunNotificationHelper.NOTIFICATION_ID, notification)
    }

    private fun postCompletion() {
        val step = plan.getOrNull(stepIndex)
        ActiveRunState.update(ActiveRunSnapshot(blockLabel, null, stepIndex, plan.size, 0, RunPhase.AWAITING_COMPLETION))
        val notification = RunNotificationHelper.completionNotification(this, blockLabel, step?.soundProfileId, hapticsEnabled)
        startForeground(RunNotificationHelper.NOTIFICATION_ID, notification)
    }

    /**
     * Stop always ends the active run; whether it leaves a trace depends on Persistent (spec
     * §6.7) — a non-persistent block is deleted entirely, a persistent one just stops running,
     * definition intact in the library. Deleting still checks references first (the same check
     * [com.clockr.app.ui.timers.BlockConfigViewModel]'s manual delete uses, via
     * [BlockRepository.usedBy]) — silently dropping a block another Chain still points at would
     * leave that Chain referencing nothing, which surfaces as a crash next time it's started.
     */
    private fun finishRun() = scope.launch {
        runJob?.cancel()
        if (!persistent) {
            val id = runningBlockId
            val label = blockLabel
            if (id != null && (label.isBlank() || repository.usedBy(label, id).isEmpty())) {
                when (runningBlockKind) {
                    BlockKind.TIMER -> db.timerDefDao().findById(id)?.let { db.timerDefDao().delete(it) }
                    BlockKind.CHAIN -> db.chainDefDao().findById(id)?.let { db.chainDefDao().delete(it) }
                    null -> {}
                }
            }
        }
        ActiveRunState.update(null)
        RunNotificationHelper.cancel(this@TimerRunService)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** A run that never got going — no block to (maybe) delete, just clean shutdown. */
    private fun abortRun(message: String?) = scope.launch {
        runJob?.cancel()
        ActiveRunState.update(null)
        RunNotificationHelper.cancel(this@TimerRunService)
        if (message != null) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(RunNotificationHelper.ERROR_NOTIFICATION_ID, RunNotificationHelper.errorNotification(this@TimerRunService, message))
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runJob?.cancel()
        scope.cancel()
        ActiveRunState.update(null)
        super.onDestroy()
    }
}
