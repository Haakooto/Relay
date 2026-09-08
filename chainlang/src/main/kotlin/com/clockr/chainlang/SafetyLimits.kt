package com.clockr.chainlang

/** Defaults from spec §6.6. */
data class SafetyLimits(
    val maxMultiplier: Int = 200,
    val maxDepth: Int = 20,
    val maxPlanSize: Int = 300,
    val maxExpressionLength: Int = 2000,
)

/**
 * Rejects a chain expression before it's ever evaluated/expanded — the nesting-bomb hedge from
 * spec §6.6. This walks the *shape* of the AST, not its evaluated values: a `repeat`/`range`/
 * `arange` count that depends on `NOW`/`RANDOM` is checked against a conservative upper bound
 * (worst case for MINUTE/SECOND is 59, HOUR is 23, RANDOM(lo, hi) is bound(hi)) rather than its
 * actual runtime value, which isn't known yet. A `Ref` is a single opaque step here — the block
 * it points to was already validated when *it* was saved; this pass never follows references.
 */
object ChainSafety {

    fun validate(source: String, root: Group, limits: SafetyLimits = SafetyLimits()): Long {
        if (source.length > limits.maxExpressionLength) {
            throw ExpressionTooLongException(source.length, limits.maxExpressionLength)
        }
        return sizeOf(root, depth = 1, limits)
    }

    private fun sizeOf(node: ChainNode, depth: Int, limits: SafetyLimits): Long {
        if (depth > limits.maxDepth) throw NestingTooDeepException(depth, limits.maxDepth)
        val size: Long = when (node) {
            is Group -> node.elements.sumOf { sizeOf(it, depth + 1, limits) }
            is Ref -> 1L
            is DurationLiteral -> 1L
            is DurationExpr -> 1L
            is RepeatCall -> {
                val n = upperBound(node.count)
                requireWithinMultiplier(n, limits)
                n * sizeOf(node.body, depth + 1, limits)
            }
            is RangeCall -> requireWithinMultiplier(estimateRangeCount(node.start, node.stop, node.step), limits)
            is ArangeCall -> requireWithinMultiplier(estimateRangeCount(node.start, node.stop, node.step), limits)
        }
        if (size > limits.maxPlanSize) throw PlanTooLargeException(size, limits.maxPlanSize)
        return size
    }

    private fun requireWithinMultiplier(n: Long, limits: SafetyLimits): Long {
        if (n > limits.maxMultiplier) throw MultiplierTooLargeException(n, limits.maxMultiplier)
        return n
    }

    /** Conservative (never an underestimate) — see the class doc. */
    private fun upperBound(expr: IntExpr): Long = when (expr) {
        is IntLiteral -> expr.value
        is IntAdd -> upperBound(expr.left) + upperBound(expr.right)
        is IntSub -> upperBound(expr.left)
        is RandomCall -> upperBound(expr.high)
        is TimeFunc -> when (expr.unit) {
            TimeUnit.HOUR -> 23L
            TimeUnit.MINUTE, TimeUnit.SECOND -> 59L
        }
    }

    private fun lowerBound(expr: IntExpr): Long = when (expr) {
        is IntLiteral -> expr.value
        is IntAdd -> lowerBound(expr.left) + lowerBound(expr.right)
        is IntSub -> lowerBound(expr.left) - upperBound(expr.right)
        is RandomCall -> lowerBound(expr.low)
        is TimeFunc -> 0L
    }

    /** Same half-open stepping as the evaluator, but on bounds instead of concrete values. */
    private fun estimateRangeCount(startExpr: IntExpr, stopExpr: IntExpr, stepExpr: IntExpr?): Long {
        val spread = (upperBound(stopExpr) - lowerBound(startExpr)).let { maxOf(it, upperBound(startExpr) - lowerBound(stopExpr)) }
        val minStep = stepExpr?.let { maxOf(1L, lowerBound(it)) } ?: 1L
        return (kotlin.math.abs(spread) + minStep - 1) / minStep
    }
}
