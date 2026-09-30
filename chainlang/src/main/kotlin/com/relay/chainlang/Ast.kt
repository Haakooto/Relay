package com.relay.chainlang

/** An element of relay content (spec §06). */
sealed interface ChainNode

/** `(a, b, c)`: a relay's content list, or a function's bracketed sub-list. */
data class Group(val elements: List<ChainNode>) : ChainNode

/** A bare name: a reference step to a saved Timer or Relay, resolved live, keeping its sound. */
data class Ref(val label: String) : ChainNode

/**
 * One anonymous step whose length is a sum of terms: `25m`, `30`, `MINUTE(NOW) + 5`, or
 * `Test2 - 2` (a name inside arithmetic is that block's total duration, spec §06). [label] is
 * the optional display label, `5m "stretch"`.
 */
data class DurationNode(val terms: List<SignedTerm>, val label: String? = null) : ChainNode

data class SignedTerm(val negative: Boolean, val term: DurationTerm)

sealed interface DurationTerm

/** An explicit `2h30m10s`-style literal, already in seconds. */
data class SecondsTerm(val seconds: Long) : DurationTerm

/** A bare number or integer expression: minutes, per the spec's rule. */
data class MinutesTerm(val minutes: IntExpr) : DurationTerm

/** A name used as a value: the referenced block's total duration. */
data class RefValueTerm(val label: String) : DurationTerm

data class RepeatCall(val count: IntExpr, val body: ChainNode) : ChainNode

/** Minutes; arguments may be fractional (`range(1, 3, 0.5)`). */
data class RangeCall(val start: IntExpr, val stop: IntExpr, val step: IntExpr?) : ChainNode

/** The arithmetic sub-language for call arguments and `MINUTE(NOW)` etc. */
sealed interface IntExpr

data class IntLiteral(val value: Long) : IntExpr
/** Only produced for `range(...)` arguments. */
data class DecimalLiteral(val value: Double) : IntExpr
data class IntAdd(val left: IntExpr, val right: IntExpr) : IntExpr
data class IntSub(val left: IntExpr, val right: IntExpr) : IntExpr
data class RandomCall(val low: IntExpr, val high: IntExpr) : IntExpr

enum class TimeUnit { MINUTE, HOUR, SECOND }

data class TimeFunc(val unit: TimeUnit, val time: TimeExpr) : IntExpr

/** `NOW` optionally offset by a literal duration, e.g. `NOW+30m`. */
data class TimeExpr(val offsetSeconds: Long)
