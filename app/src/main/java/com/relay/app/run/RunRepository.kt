package com.relay.app.run

import com.relay.app.data.library.TransitionMode
import com.relay.app.data.library.TransitionTone
import com.relay.app.data.run.RunDao
import com.relay.app.data.run.RunEntity
import com.relay.app.data.run.RunKind
import com.relay.app.data.run.RunPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** AlarmManager side for runs. Implemented by [RunScheduler]; faked in tests. */
interface RunArming {
    fun armStepEnd(runId: String, stepIndex: Int, atMillis: Long)
    fun cancel(runId: String)
}

/** Notification and ringing side for runs. Implemented by [RunNotifications]. */
interface RunNotices {
    suspend fun show(view: RunView)
    fun clear(runId: String)
    /** Stops a ring for this run if one is sounding (the user acted elsewhere). */
    fun cancelRing(runId: String)
}

/** A run plus its decoded plan, for the UI and notifications. */
data class RunView(val run: RunEntity, val plan: List<RunStep>) {
    val step: RunStep get() = plan[run.stepIndex.coerceIn(0, plan.lastIndex)]
    val isRelay: Boolean get() = run.kind == RunKind.RELAY
    val isLastStep: Boolean get() = run.stepIndex >= plan.lastIndex
}

sealed interface StepDecision {
    /** Stale or duplicate alarm. */
    data object Ignore : StepDecision
    /** Seamless boundary: play the cue, the next step already runs. */
    data class Advanced(val tone: TransitionTone) : StepDecision
    /** Interaction boundary or the end: ring through the firing module. */
    data class Ring(val view: RunView) : StepDecision
}

/**
 * The command layer for runs (spec §05/§13): every run change goes through here. Timing is
 * driven by one exact alarm per run, for the current step's end; the process may die between
 * steps and nothing is lost. One mutex serializes all writes.
 */
class RunRepository(
    private val dao: RunDao,
    private val arming: RunArming,
    private val notices: RunNotices,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    fun observe(): Flow<List<RunView>> = dao.observeAll().map { runs -> runs.mapNotNull(::view) }

    suspend fun get(runId: String): RunView? = dao.get(runId)?.let(::view)

    suspend fun start(kind: RunKind, sourceDefId: String?, label: String, plan: List<RunStep>, startedBy: String = "user"): RunView =
        mutex.withLock {
            require(plan.isNotEmpty()) { "empty plan" }
            val t = now()
            val run = RunEntity(
                id = UUID.randomUUID().toString(), kind = kind, sourceDefId = sourceDefId, label = label.trim(),
                plan = RunPlans.encode(plan), stepIndex = 0, phase = RunPhase.RUNNING,
                stepEndsAtMillis = t + plan[0].seconds * 1000, remainingMillis = null, alertSinceMillis = null,
                startedAtMillis = t, startedBy = startedBy,
            )
            dao.upsert(run)
            publish(run)
        }

    suspend fun pause(runId: String) = update(runId) { run, _ ->
        if (run.phase != RunPhase.RUNNING) return@update null
        run.copy(phase = RunPhase.PAUSED, remainingMillis = ((run.stepEndsAtMillis ?: now()) - now()).coerceAtLeast(0), stepEndsAtMillis = null)
    }

    suspend fun resume(runId: String) = update(runId) { run, _ ->
        if (run.phase != RunPhase.PAUSED) return@update null
        run.copy(phase = RunPhase.RUNNING, stepEndsAtMillis = now() + (run.remainingMillis ?: 0), remainingMillis = null)
    }

    /** Back to the first step at full length, paused (stock-timer reset). */
    suspend fun reset(runId: String) = update(runId) { run, plan ->
        run.copy(
            stepIndex = 0, phase = RunPhase.PAUSED, stepEndsAtMillis = null,
            remainingMillis = plan[0].seconds * 1000, alertSinceMillis = null,
        )
    }

    /** +1 min on the current step; at the end, extends the last step in place and runs again. */
    suspend fun addMinute(runId: String) = update(runId) { run, plan ->
        val minute = 60_000L
        when (run.phase) {
            RunPhase.RUNNING -> run.copy(stepEndsAtMillis = (run.stepEndsAtMillis ?: now()) + minute)
            RunPhase.PAUSED -> run.copy(remainingMillis = (run.remainingMillis ?: 0) + minute)
            RunPhase.DONE -> run.copy(
                phase = RunPhase.RUNNING, stepIndex = plan.lastIndex, stepEndsAtMillis = now() + minute, alertSinceMillis = null,
            )
            RunPhase.AWAITING -> null
        }
    }

    /** At an interaction boundary: start the next step now. */
    suspend fun continueRun(runId: String) = update(runId) { run, plan ->
        if (run.phase != RunPhase.AWAITING) return@update null
        val next = (run.stepIndex + 1).coerceAtMost(plan.lastIndex)
        run.copy(stepIndex = next, phase = RunPhase.RUNNING, stepEndsAtMillis = now() + plan[next].seconds * 1000, alertSinceMillis = null)
    }

    /** From the end: the whole thing again from the first step. */
    suspend fun restart(runId: String) = update(runId) { run, plan ->
        run.copy(stepIndex = 0, phase = RunPhase.RUNNING, stepEndsAtMillis = now() + plan[0].seconds * 1000, remainingMillis = null, alertSinceMillis = null)
    }

    /** Ends the run. A saved definition stays in the library; an unsaved one is simply gone. */
    suspend fun stop(runId: String) = mutex.withLock {
        arming.cancel(runId)
        notices.cancelRing(runId)
        notices.clear(runId)
        dao.delete(runId)
    }

    /**
     * The step-end alarm fired. Idempotent: an alarm for a step the run has already left, or one
     * arriving before the (possibly extended) end time, is ignored.
     */
    suspend fun onStepEnded(runId: String, stepIndex: Int): StepDecision = mutex.withLock {
        val run = dao.get(runId) ?: return StepDecision.Ignore
        val plan = decode(run) ?: return StepDecision.Ignore
        val t = now()
        val endsAt = run.stepEndsAtMillis
        if (run.phase != RunPhase.RUNNING || run.stepIndex != stepIndex || endsAt == null || t < endsAt - EARLY_TOLERANCE_MILLIS) {
            return StepDecision.Ignore
        }
        val step = plan[stepIndex]
        val updated = when {
            stepIndex >= plan.lastIndex -> run.copy(phase = RunPhase.DONE, stepEndsAtMillis = null, alertSinceMillis = t)
            step.transitionAfter == TransitionMode.INTERACTION ->
                run.copy(phase = RunPhase.AWAITING, stepEndsAtMillis = null, alertSinceMillis = t)
            // Seamless: the next step starts at the scheduled end, not "now", so a late alarm
            // doesn't stretch the relay. If that end has also passed, its alarm fires at once.
            else -> run.copy(stepIndex = stepIndex + 1, stepEndsAtMillis = endsAt + plan[stepIndex + 1].seconds * 1000)
        }
        dao.upsert(updated)
        val view = publishLocked(updated)
        if (updated.phase == RunPhase.RUNNING) StepDecision.Advanced(step.toneAfter) else StepDecision.Ring(view)
    }

    /** Re-arms every running run (boot, app start). Never changes state: see [onStepEnded]. */
    suspend fun rearmAll() = mutex.withLock {
        for (run in dao.getAll()) {
            val view = view(run) ?: continue
            if (run.phase == RunPhase.RUNNING && run.stepEndsAtMillis != null) arming.armStepEnd(run.id, run.stepIndex, run.stepEndsAtMillis)
            notices.show(view)
        }
    }

    private suspend fun update(runId: String, change: (RunEntity, List<RunStep>) -> RunEntity?): RunView? = mutex.withLock {
        val run = dao.get(runId) ?: return null
        val plan = decode(run) ?: return null
        val updated = change(run, plan) ?: return view(run)
        dao.upsert(updated)
        if (updated.phase != RunPhase.AWAITING && updated.phase != RunPhase.DONE) notices.cancelRing(runId)
        publishLocked(updated)
    }

    private suspend fun publish(run: RunEntity): RunView = publishLocked(run)

    /** Arms (or cancels) the step-end alarm to match [run], and refreshes its notification. */
    private suspend fun publishLocked(run: RunEntity): RunView {
        val view = view(run)!!
        if (run.phase == RunPhase.RUNNING && run.stepEndsAtMillis != null) arming.armStepEnd(run.id, run.stepIndex, run.stepEndsAtMillis)
        else arming.cancel(run.id)
        notices.show(view)
        return view
    }

    private fun decode(run: RunEntity): List<RunStep>? = runCatching { RunPlans.decode(run.plan) }.getOrNull()?.takeIf { it.isNotEmpty() }
    private fun view(run: RunEntity): RunView? = decode(run)?.let { RunView(run, it) }

    companion object {
        const val EARLY_TOLERANCE_MILLIS = 1_000L
    }
}
