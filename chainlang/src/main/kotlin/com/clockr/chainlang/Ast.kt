package com.clockr.chainlang

/** An element of chain content (spec §6.3/§6.4). */
sealed interface ChainNode

/** `(a, b, c)` — a chain's content list, or a function's bracketed sub-chain argument. */
data class Group(val elements: List<ChainNode>) : ChainNode

/** The label of an existing persistent Timer or Chain, resolved live at evaluation time. */
data class Ref(val label: String) : ChainNode

/** An explicit `2h30m10s`-style literal — already an exact number of seconds. */
data class DurationLiteral(val seconds: Long) : ChainNode

/** A bare number or arithmetic expression used as a duration — minutes, per the spec's rule. */
data class DurationExpr(val minutes: IntExpr) : ChainNode

data class RepeatCall(val count: IntExpr, val body: ChainNode) : ChainNode
data class RangeCall(val start: IntExpr, val stop: IntExpr, val step: IntExpr?) : ChainNode
data class ArangeCall(val start: IntExpr, val stop: IntExpr, val step: IntExpr?) : ChainNode

/** The plain-integer arithmetic sub-language used for call arguments and `MINUTE(NOW)` etc. */
sealed interface IntExpr

data class IntLiteral(val value: Long) : IntExpr
data class IntAdd(val left: IntExpr, val right: IntExpr) : IntExpr
data class IntSub(val left: IntExpr, val right: IntExpr) : IntExpr
data class RandomCall(val low: IntExpr, val high: IntExpr) : IntExpr

enum class TimeUnit { MINUTE, HOUR, SECOND }

data class TimeFunc(val unit: TimeUnit, val time: TimeExpr) : IntExpr

/** `NOW` optionally offset by a literal duration, e.g. `NOW+30m`. */
data class TimeExpr(val offsetSeconds: Long)
