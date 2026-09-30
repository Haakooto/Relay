package com.relay.app.run

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

/**
 * A run's step ended. Decides with [RunRepository.onStepEnded], then either plays the seamless
 * cue right here or hands the ring to [RingService]. Starting that service from the background
 * is allowed during an exact-alarm broadcast.
 */
class RunStepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val runId = intent.data?.lastPathSegment ?: return
        val stepIndex = intent.getIntExtra(EXTRA_STEP_INDEX, -1)
        val container = context.appContainer
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (val decision = container.runs.onStepEnded(runId, stepIndex)) {
                    is StepDecision.Advanced -> TransitionBeep.play(decision.tone)
                    is StepDecision.Ring -> startRing(context, runId)
                    StepDecision.Ignore -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "step end for $runId failed; ringing anyway", e)
                startRing(context, runId)
            } finally {
                pending.finish()
            }
        }
    }

    private fun startRing(context: Context, runId: String) {
        try {
            context.startForegroundService(RingService.runIntent(context, runId))
        } catch (e: Exception) {
            Log.e(TAG, "can't start RingService for run $runId", e)
            context.appContainer.firingNotifications.showFallback("run:$runId")
        }
    }

    companion object {
        const val EXTRA_STEP_INDEX = "stepIndex"
        private const val TAG = "RunStepReceiver"
    }
}

/** Buttons on a run's ongoing notification. */
class RunActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val runId = intent.getStringExtra(EXTRA_RUN_ID) ?: return
        val runs = context.appContainer.runs
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_PAUSE -> runs.pause(runId)
                    ACTION_RESUME -> runs.resume(runId)
                    ACTION_ADD_MINUTE -> runs.addMinute(runId)
                    ACTION_STOP -> runs.stop(runId)
                    ACTION_CONTINUE -> runs.continueRun(runId)
                    ACTION_RESTART -> runs.restart(runId)
                }
            } catch (e: Exception) {
                Log.e("RunActionReceiver", "${intent.action} on $runId failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.relay.app.run.PAUSE"
        const val ACTION_RESUME = "com.relay.app.run.RESUME"
        const val ACTION_ADD_MINUTE = "com.relay.app.run.ADD_MINUTE"
        const val ACTION_STOP = "com.relay.app.run.STOP"
        const val ACTION_CONTINUE = "com.relay.app.run.CONTINUE"
        const val ACTION_RESTART = "com.relay.app.run.RESTART"
        const val EXTRA_RUN_ID = "runId"
    }
}
