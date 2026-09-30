package com.relay.chainlang

import kotlin.random.Random

/** Wall-clock time-of-day, in whole seconds — enough for `NOW`/`MINUTE`/`HOUR`/`SECOND`. */
data class NowClock(val hour: Int, val minute: Int, val second: Int) {
    fun plusSeconds(delta: Long): NowClock {
        val totalNow = hour * 3600L + minute * 60L + second
        val total = ((totalNow + delta) % 86400L + 86400L) % 86400L
        return NowClock((total / 3600L).toInt(), ((total % 3600L) / 60L).toInt(), (total % 60L).toInt())
    }

    companion object {
        fun now(): NowClock = java.time.LocalTime.now().let { NowClock(it.hour, it.minute, it.second) }
    }
}

/** What the evaluator needs to know about a referenced persistent block — supplied by the store. */
data class ResolvedBlockInfo(val durationSeconds: Long, val soundProfile: String?, val isChain: Boolean)

fun interface BlockResolver {
    /** @throws UnknownReferenceException if [label] isn't a known persistent block. */
    fun resolve(label: String): ResolvedBlockInfo
}

/** One step of a chain's own execution plan — a reference stays opaque, it is never inlined. */
sealed interface ResolvedStep {
    val durationSeconds: Long
}

data class LeafStep(override val durationSeconds: Long, val soundProfile: String?) : ResolvedStep
data class BlockRefStep(val refLabel: String, override val durationSeconds: Long, val soundProfile: String?) : ResolvedStep

class Evaluator(
    private val now: NowClock,
    private val resolver: BlockResolver,
    private val random: Random = Random.Default,
    private val defaultSoundProfile: String? = null,
) {
    /** Flattens one chain's own `Group` — `repeat`/`range`/`arange` expand, `Ref`s stay opaque. */
    fun evaluate(root: Group): List<ResolvedStep> = root.elements.flatMap { evalElement(it) }

    private fun evalElement(node: ChainNode): List<ResolvedStep> = when (node) {
        is Group -> node.elements.flatMap { evalElement(it) }
        is Ref -> {
            val block = resolver.resolve(node.label)
            listOf(BlockRefStep(node.label, block.durationSeconds, block.soundProfile))
        }
        is DurationLiteral -> listOf(LeafStep(node.seconds, defaultSoundProfile))
        is DurationExpr -> listOf(LeafStep(evalInt(node.minutes) * 60, defaultSoundProfile))
        is RepeatCall -> {
            val n = evalInt(node.count)
            List(n.toInt()) { evalElement(node.body) }.flatten()
        }
        is RangeCall -> evalRangeLike(node.start, node.stop, node.step).map { LeafStep(it * 60, defaultSoundProfile) }
        is ArangeCall -> evalRangeLike(node.start, node.stop, node.step).map { LeafStep(it * 60, defaultSoundProfile) }
    }

    /** `range`/`arange` share numpy's half-open, direction-aware stepping. */
    private fun evalRangeLike(startExpr: IntExpr, stopExpr: IntExpr, stepExpr: IntExpr?): List<Long> {
        val start = evalInt(startExpr)
        val stop = evalInt(stopExpr)
        val rawStep = stepExpr?.let { evalInt(it) } ?: 1L
        if (rawStep == 0L) throw ChainSyntaxException("range/arange step must not be 0", -1)
        val step = if (stop < start) -kotlin.math.abs(rawStep) else kotlin.math.abs(rawStep)

        val values = mutableListOf<Long>()
        var v = start
        while (if (step > 0) v < stop else v > stop) {
            values += v
            v += step
        }
        return values
    }

    private fun evalInt(expr: IntExpr): Long = when (expr) {
        is IntLiteral -> expr.value
        is IntAdd -> evalInt(expr.left) + evalInt(expr.right)
        is IntSub -> evalInt(expr.left) - evalInt(expr.right)
        is RandomCall -> {
            val lo = evalInt(expr.low)
            val hi = evalInt(expr.high)
            if (lo > hi) throw ChainSyntaxException("RANDOM(lo, hi) needs lo <= hi", -1)
            random.nextLong(lo, hi + 1)
        }
        is TimeFunc -> {
            val clock = now.plusSeconds(expr.time.offsetSeconds)
            when (expr.unit) {
                TimeUnit.MINUTE -> clock.minute.toLong()
                TimeUnit.HOUR -> clock.hour.toLong()
                TimeUnit.SECOND -> clock.second.toLong()
            }
        }
    }
}
