package com.clockr.app.run

import com.clockr.app.data.BlockRepository
import com.clockr.app.data.ChainDefEntity
import com.clockr.app.data.ClockrDatabase
import com.clockr.app.data.TimerDefEntity
import com.clockr.app.domain.TransitionMode
import com.clockr.chainlang.BlockRefStep
import com.clockr.chainlang.ChainLang
import com.clockr.chainlang.LeafStep
import com.clockr.chainlang.NowClock
import com.clockr.chainlang.SafetyLimits

/**
 * Turns a Timer or Chain definition into a flat, ordered [RunStep] list the run engine can just
 * walk start to finish.
 *
 * chainlang's own [ChainLang.run] already resolves one chain's content, but deliberately treats
 * a reference to another *chain* as a single opaque step (spec §6.2/§6.3 — that's the right
 * behavior for previews and duration totals, e.g. "Session" shows as one 2h pill inside
 * "Workday"). Actually *running* a chain needs the opposite: a referenced chain's own steps
 * (Work, Break, Work, Break...) have to play out individually, each under its own sound and the
 * *referenced* chain's own transition mode — only the boundary leaving the referenced chain back
 * into the parent uses the parent's transition mode. This builder does that recursive expansion
 * from the app side, using only chainlang's public API (never its internal Parser/Evaluator) —
 * chainlang itself stays exactly as designed for preview purposes.
 */
class RunPlanBuilder(private val db: ClockrDatabase, private val repository: BlockRepository) {

    suspend fun buildForTimer(timer: TimerDefEntity): List<RunStep> {
        val seconds = repository.evalDurationPreview(timer.durationExpr)
        return listOf(RunStep(timer.label.ifBlank { null }, seconds, timer.soundProfileId, TransitionMode.SEAMLESS))
    }

    suspend fun buildForChain(chain: ChainDefEntity, limits: SafetyLimits = SafetyLimits()): List<RunStep> {
        val transitionMode = TransitionMode.valueOf(chain.transitionMode)
        val overrides = chain.soundOverrideMap()
        val resolved = ChainLang.run(chain.contentExpr, NowClock.now(), resolver = repository, limits = limits)

        val steps = mutableListOf<RunStep>()
        for (step in resolved) {
            when (step) {
                is LeafStep -> steps += RunStep(
                    label = null,
                    seconds = step.durationSeconds,
                    soundProfileId = step.soundProfile ?: chain.defaultSoundProfileId,
                    transitionMode = transitionMode,
                )
                is BlockRefStep -> {
                    val subChain = db.chainDefDao().findByLabel(step.refLabel)
                    if (subChain != null) {
                        val subSteps = buildForChain(subChain, limits).toMutableList()
                        if (subSteps.isNotEmpty()) {
                            // Leaving the referenced chain is a boundary the *parent* governs.
                            subSteps[subSteps.lastIndex] = subSteps.last().copy(transitionMode = transitionMode)
                        }
                        steps += subSteps
                    } else {
                        steps += RunStep(
                            label = step.refLabel,
                            seconds = step.durationSeconds,
                            soundProfileId = overrides[step.refLabel] ?: step.soundProfile,
                            transitionMode = transitionMode,
                        )
                    }
                }
            }
        }
        return steps
    }
}
