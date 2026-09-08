package com.clockr.app.data

import com.clockr.chainlang.BlockRefStep
import com.clockr.chainlang.BlockResolver
import com.clockr.chainlang.ChainLang
import com.clockr.chainlang.CycleChecker
import com.clockr.chainlang.NowClock
import com.clockr.chainlang.ResolvedBlockInfo
import com.clockr.chainlang.ResolvedStep
import com.clockr.chainlang.SafetyLimits
import com.clockr.chainlang.UnknownReferenceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridges Room to chainlang: this *is* the [BlockResolver] the evaluator calls back into for
 * every `Ref` it hits, so a reference is resolved by recursing straight back into this class —
 * a chain reference (`Session` inside `Workday`) evaluates its own content the same way a
 * top-level run would, never inlined into the caller's step list (spec §6.2/§6.3).
 *
 * [findByLabelBlocking] calls happen on the calling thread — every public entry point here is
 * `suspend` and wraps its work in [Dispatchers.IO], which is what makes calling them safe.
 */
class BlockRepository(private val db: ClockrDatabase) : BlockResolver {

    override fun resolve(label: String): ResolvedBlockInfo {
        db.timerDefDao().findByLabelBlocking(label)?.let { timer ->
            val seconds = ChainLang.evalSingleDuration(timer.durationExpr, NowClock.now())
            return ResolvedBlockInfo(seconds, timer.soundProfileId, isChain = false)
        }
        val chain = db.chainDefDao().findByLabelBlocking(label) ?: throw UnknownReferenceException(label)
        val steps = ChainLang.run(chain.contentExpr, NowClock.now(), resolver = this)
        return ResolvedBlockInfo(steps.sumOf { it.durationSeconds }, chain.defaultSoundProfileId, isChain = true)
    }

    suspend fun resolvedSteps(chain: ChainDefEntity): List<ResolvedStep> = withContext(Dispatchers.IO) {
        ChainLang.run(chain.contentExpr, NowClock.now(), resolver = this@BlockRepository)
    }

    suspend fun totalDurationSeconds(label: String): Long = withContext(Dispatchers.IO) {
        resolve(label).durationSeconds
    }

    /** For a config screen's live preview — parses/validates without persisting anything. */
    suspend fun evalDurationPreview(source: String): Long = withContext(Dispatchers.IO) {
        ChainLang.evalSingleDuration(source, NowClock.now())
    }

    /** Same idea, for a Chain's content expression — each pair is (ref label or null, seconds). */
    suspend fun previewChainSteps(source: String, limits: SafetyLimits = SafetyLimits()): List<Pair<String?, Long>> =
        withContext(Dispatchers.IO) {
            ChainLang.run(source, NowClock.now(), resolver = this@BlockRepository, limits = limits)
                .map { step -> (step as? BlockRefStep)?.refLabel to step.durationSeconds }
        }

    /**
     * Validates and saves a Timer definition: the duration expression itself must parse
     * (spec §6.2's safety hedge applies here too, via [ChainLang.evalSingleDuration]).
     */
    suspend fun saveTimer(entity: TimerDefEntity): Unit = withContext(Dispatchers.IO) {
        ChainLang.evalSingleDuration(entity.durationExpr, NowClock.now())
        db.timerDefDao().upsert(entity)
    }

    /**
     * Validates and saves a Chain definition — parses + runs the safety hedge on its own
     * content, then checks the *whole store's* reference graph is still a DAG including this
     * chain's edges before persisting (spec §6.6: cycle detection is mandatory, not optional).
     */
    suspend fun saveChain(entity: ChainDefEntity, limits: SafetyLimits = SafetyLimits()): Unit = withContext(Dispatchers.IO) {
        ChainLang.parseAndValidate(entity.contentExpr, limits)
        val directRefs = ChainLang.referencedLabels(entity.contentExpr)

        val otherChainsByLabel = db.chainDefDao().getAll()
            .filter { it.id != entity.id }
            .associateBy { it.label }
        val allLabels = otherChainsByLabel.keys + entity.label

        CycleChecker.checkAcyclic(allLabels) { label ->
            if (label == entity.label) directRefs
            else otherChainsByLabel[label]?.let { ChainLang.referencedLabels(it.contentExpr) } ?: emptySet()
        }

        db.chainDefDao().upsert(entity)
    }
}
