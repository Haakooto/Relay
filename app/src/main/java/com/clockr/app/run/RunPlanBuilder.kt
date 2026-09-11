package com.clockr.app.run

import com.clockr.app.data.BlockRepository
import com.clockr.app.data.ChainDefEntity
import com.clockr.app.data.ClockrDatabase
import com.clockr.app.data.TimerDefEntity
import com.clockr.app.data.TransitionBeepTone
import com.clockr.app.domain.TransitionMode
import com.clockr.chainlang.BlockRefStep
import com.clockr.chainlang.ChainLang
import com.clockr.chainlang.LeafStep
import com.clockr.chainlang.NowClock
import com.clockr.chainlang.SafetyLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 *
 * [BlockRepository.resolve] (which [ChainLang.run] calls back into for every reference) goes
 * through Room's *blocking* DAO methods, not the suspend ones — [BlockResolver.resolve] is a
 * synchronous callback, so it has no other way to call into Room. That makes every public
 * function here IO-bound the same way [BlockRepository]'s own suspend functions are: always
 * entered via `withContext(Dispatchers.IO)`, never left to whatever dispatcher the caller
 * happens to be on (the run engine calls this from `Dispatchers.Main.immediate`, where a
 * blocking Room query throws rather than just risking jank).
 */
class RunPlanBuilder(private val db: ClockrDatabase, private val repository: BlockRepository) {

    suspend fun buildForTimer(timer: TimerDefEntity): List<RunStep> = withContext(Dispatchers.IO) {
        val seconds = repository.evalDurationPreview(timer.durationExpr)
        // A standalone Timer is always its own last (only) step, so transitionMode/BeepTone here
        // never actually govern a boundary — the values are inert placeholders.
        listOf(RunStep(timer.label.ifBlank { null }, seconds, timer.soundProfileId, TransitionMode.SEAMLESS, TransitionBeepTone.BEEP))
    }

    suspend fun buildForChain(chain: ChainDefEntity, limits: SafetyLimits = SafetyLimits()): List<RunStep> = withContext(Dispatchers.IO) {
        val transitionMode = TransitionMode.valueOf(chain.transitionMode)
        val beepTone = runCatching { TransitionBeepTone.valueOf(chain.transitionBeepTone) }.getOrDefault(TransitionBeepTone.BEEP)
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
                    transitionBeepTone = beepTone,
                )
                is BlockRefStep -> {
                    val subChain = db.chainDefDao().findByLabel(step.refLabel)
                    if (subChain != null) {
                        val subSteps = buildForChain(subChain, limits).toMutableList()
                        if (subSteps.isNotEmpty()) {
                            // Leaving the referenced chain is a boundary the *parent* governs —
                            // its transition mode AND its own beep tone, not the sub-chain's.
                            subSteps[subSteps.lastIndex] = subSteps.last().copy(
                                transitionMode = transitionMode,
                                transitionBeepTone = beepTone,
                            )
                        }
                        steps += subSteps
                    } else {
                        steps += RunStep(
                            label = step.refLabel,
                            seconds = step.durationSeconds,
                            soundProfileId = overrides[step.refLabel] ?: step.soundProfile,
                            transitionMode = transitionMode,
                            transitionBeepTone = beepTone,
                        )
                    }
                }
            }
        }
        steps
    }
}
