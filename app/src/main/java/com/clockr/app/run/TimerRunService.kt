package com.clockr.app.run

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.clockr.app.data.BlockRepository
import com.clockr.app.data.ClockrDatabase
import com.clockr.app.data.SettingsRepository
import com.clockr.app.domain.BlockKind
import com.clockr.app.domain.TransitionMode
import com.clockr.chainlang.SafetyLimits
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
                val blockId = intent.getStringExtra(EXTRA_BLOCK_ID) ?: return START_NOT_STICKY
                val kind = intent.getStringExtra(EXTRA_BLOCK_KIND)?.let { BlockKind.valueOf(it) } ?: return START_NOT_STICKY
                startForeground(RunNotificationHelper.NOTIFICATION_ID, RunNotificationHelper.startingNotification(this))
                beginRun(blockId, kind)
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

    private fun beginRun(blockId: String, kind: BlockKind) = scope.launch {
        runJob?.cancel()
        runningBlockId = blockId
        runningBlockKind = kind
        val limits = SafetyLimits(maxPlanSize = settingsRepository.current().maxExecutionPlanSize)
        when (kind) {
            BlockKind.TIMER -> {
                val timer = db.timerDefDao().findById(blockId)
                if (timer == null) { finishRun(); return@launch }
                blockLabel = timer.label.ifBlank { "Timer" }
                persistent = timer.persistent
                plan = RunPlanBuilder(db, repository).buildForTimer(timer)
            }
            BlockKind.CHAIN -> {
                val chain = db.chainDefDao().findById(blockId)
                if (chain == null) { finishRun(); return@launch }
                blockLabel = chain.label.ifBlank { "Chain" }
                persistent = chain.persistent
                plan = RunPlanBuilder(db, repository).buildForChain(chain, limits)
            }
        }
        if (plan.isEmpty()) { finishRun(); return@launch }
        stepIndex = 0
        runFromCurrentStep()
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
                    stepIndex++
                    continue
                } else {
                    postAwaitingInteraction(step)
                    return@launch
                }
            }
        }
    }

    private fun postRunning(step: RunStep) {
        ActiveRunState.update(ActiveRunSnapshot(blockLabel, step.label, stepIndex, plan.size, step.seconds, RunPhase.RUNNING))
        val notification = RunNotificationHelper.runningNotification(this, blockLabel, step, stepIndex, plan.size)
        startForeground(RunNotificationHelper.NOTIFICATION_ID, notification)
    }

    private fun postAwaitingInteraction(step: RunStep) {
        ActiveRunState.update(ActiveRunSnapshot(blockLabel, step.label, stepIndex, plan.size, 0, RunPhase.AWAITING_INTERACTION))
        val notification = RunNotificationHelper.awaitingInteractionNotification(this, blockLabel, step, stepIndex, plan.size)
        startForeground(RunNotificationHelper.NOTIFICATION_ID, notification)
    }

    private fun postCompletion() {
        ActiveRunState.update(ActiveRunSnapshot(blockLabel, null, stepIndex, plan.size, 0, RunPhase.AWAITING_COMPLETION))
        val notification = RunNotificationHelper.completionNotification(this, blockLabel)
        startForeground(RunNotificationHelper.NOTIFICATION_ID, notification)
    }

    /**
     * Stop always ends the active run; whether it leaves a trace depends on Persistent (spec
     * §6.7) — a non-persistent block is deleted entirely, a persistent one just stops running,
     * definition intact in the library.
     */
    private fun finishRun() = scope.launch {
        runJob?.cancel()
        if (!persistent) {
            when (runningBlockKind) {
                BlockKind.TIMER -> runningBlockId?.let { id -> db.timerDefDao().findById(id)?.let { db.timerDefDao().delete(it) } }
                BlockKind.CHAIN -> runningBlockId?.let { id -> db.chainDefDao().findById(id)?.let { db.chainDefDao().delete(it) } }
                null -> {}
            }
        }
        ActiveRunState.update(null)
        RunNotificationHelper.cancel(this@TimerRunService)
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
