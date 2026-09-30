package com.relay.chainlang

/** Defaults from spec §6.6. */
data class SafetyLimits(
    val maxMultiplier: Int = 200,
    val maxDepth: Int = 20,
    val maxPlanSize: Int = 300,
    val maxExpressionLength: Int = 2000,
)

/**
 * Rejects a chain expression before it's ever evaluated/expanded — the nesting-bomb hedge from
 * spec §6.6. This walks the *shape* of the AST, not its evaluated values: a `repeat`/`range`
 * count that depends on `NOW`/`RANDOM` is checked against a conservative upper bound
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
            is DurationNode -> 1L
            is RepeatCall -> {
                val n = kotlin.math.ceil(upperBound(node.count)).toLong()
                requireWithinMultiplier(n, limits)
                n * sizeOf(node.body, depth + 1, limits)
            }
            is RangeCall -> requireWithinMultiplier(estimateRangeCount(node.start, node.stop, node.step), limits)
        }
        if (size > limits.maxPlanSize) throw PlanTooLargeException(size, limits.maxPlanSize)
        return size
    }

    private fun requireWithinMultiplier(n: Long, limits: SafetyLimits): Long {
        if (n > limits.maxMultiplier) throw MultiplierTooLargeException(n, limits.maxMultiplier)
        return n
    }

    /** Conservative (never an underestimate), see the class doc. */
    private fun upperBound(expr: IntExpr): Double = when (expr) {
        is IntLiteral -> expr.value.toDouble()
        is DecimalLiteral -> expr.value
        is IntAdd -> upperBound(expr.left) + upperBound(expr.right)
        is IntSub -> upperBound(expr.left) - lowerBound(expr.right)
        is RandomCall -> upperBound(expr.high)
        is TimeFunc -> when (expr.unit) {
            TimeUnit.HOUR -> 23.0
            TimeUnit.MINUTE, TimeUnit.SECOND -> 59.0
        }
    }

    private fun lowerBound(expr: IntExpr): Double = when (expr) {
        is IntLiteral -> expr.value.toDouble()
        is DecimalLiteral -> expr.value
        is IntAdd -> lowerBound(expr.left) + lowerBound(expr.right)
        is IntSub -> lowerBound(expr.left) - upperBound(expr.right)
        is RandomCall -> lowerBound(expr.low)
        is TimeFunc -> 0.0
    }

    /** Same half-open stepping as the evaluator, but on bounds, so a fractional step counts right. */
    private fun estimateRangeCount(startExpr: IntExpr, stopExpr: IntExpr, stepExpr: IntExpr?): Long {
        val spread = maxOf(upperBound(stopExpr) - lowerBound(startExpr), upperBound(startExpr) - lowerBound(stopExpr))
        val minStep = stepExpr?.let { kotlin.math.abs(lowerBound(it)).coerceAtLeast(MIN_STEP) } ?: 1.0
        return kotlin.math.ceil(kotlin.math.abs(spread) / minStep).toLong()
    }

    /** A step bound that could be ~0 (e.g. `RANDOM(0, 2)`) must not divide into infinity. */
    private const val MIN_STEP = 0.01
}
