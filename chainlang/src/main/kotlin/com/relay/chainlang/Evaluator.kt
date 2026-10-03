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

/** One step of a relay's own execution plan: a reference stays opaque, it is never inlined. */
sealed interface ResolvedStep {
    val durationSeconds: Long
}

/**
 * An anonymous step. [soundProfile] is set when the step's length came from arithmetic on
 * exactly one name (`Test2 - 2`): it inherits that block's sound (spec §06). Otherwise it's the
 * evaluator's default, usually null for "use the relay's default".
 */
data class LeafStep(
    override val durationSeconds: Long,
    val soundProfile: String?,
    val label: String? = null,
) : ResolvedStep

data class BlockRefStep(val refLabel: String, override val durationSeconds: Long, val soundProfile: String?) : ResolvedStep

class Evaluator(
    private val now: NowClock,
    private val resolver: BlockResolver,
    private val random: Random = Random.Default,
    private val defaultSoundProfile: String? = null,
) {
    /** Flattens one relay's own `Group`: `repeat`/`range` expand, `Ref`s stay opaque. */
    fun evaluate(root: Group): List<ResolvedStep> = root.elements.flatMap { evalElement(it) }

    private fun evalElement(node: ChainNode): List<ResolvedStep> = when (node) {
        is Group -> node.elements.flatMap { evalElement(it) }
        is Ref -> {
            val block = resolver.resolve(node.label)
            listOf(BlockRefStep(node.label, block.durationSeconds, block.soundProfile))
        }
        is DurationNode -> listOf(evalDuration(node))
        is RepeatCall -> {
            val n = evalInt(node.count)
            List(n.toInt()) { evalElement(node.body) }.flatten()
        }
        is RangeCall -> evalRange(node.start, node.stop, node.step).map { LeafStep(positive(it, "range"), defaultSoundProfile) }
    }

    private fun evalDuration(node: DurationNode): LeafStep {
        var seconds = 0L
        val refs = mutableListOf<ResolvedBlockInfo>()
        for ((negative, term) in node.terms) {
            val value = when (term) {
                is SecondsTerm -> term.seconds
                is MinutesTerm -> evalInt(term.minutes) * 60
                is RefValueTerm -> resolver.resolve(term.label).also { refs += it }.durationSeconds
            }
            seconds += if (negative) -value else value
        }
        val sound = refs.singleOrNull()?.soundProfile ?: defaultSoundProfile
        return LeafStep(positive(seconds, node.label ?: "step"), sound, node.label)
    }

    private fun positive(seconds: Long, what: String): Long {
        if (seconds <= 0) throw NonPositiveDurationException(what, seconds)
        return seconds
    }

    /**
     * Inclusive range in minutes, returned as whole seconds: the end is included when it's a
     * whole number of steps from the start (`range(1, 3)` is 1, 2, 3; `range(1, 4, 2)` is 1, 3).
     * Direction-aware, and computed as start + i * step so 0.1 steps don't drift.
     */
    private fun evalRange(startExpr: IntExpr, stopExpr: IntExpr, stepExpr: IntExpr?): List<Long> {
        val start = evalNum(startExpr)
        val stop = evalNum(stopExpr)
        val rawStep = stepExpr?.let { evalNum(it) } ?: 1.0
        if (rawStep == 0.0) throw ChainSyntaxException("range step must not be 0", -1)
        val step = if (stop < start) -kotlin.math.abs(rawStep) else kotlin.math.abs(rawStep)
        val epsilon = 1e-9
        val values = mutableListOf<Long>()
        var i = 0
        while (true) {
            val v = start + i * step
            if (if (step > 0) v > stop + epsilon else v < stop - epsilon) break
            values += kotlin.math.round(v * 60).toLong()
            i++
        }
        return values
    }

    private fun evalNum(expr: IntExpr): Double = when (expr) {
        is DecimalLiteral -> expr.value
        is IntAdd -> evalNum(expr.left) + evalNum(expr.right)
        is IntSub -> evalNum(expr.left) - evalNum(expr.right)
        else -> evalInt(expr).toDouble()
    }

    private fun evalInt(expr: IntExpr): Long = when (expr) {
        is IntLiteral -> expr.value
        is DecimalLiteral -> throw ChainSyntaxException("decimals only work inside range()", -1)
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
