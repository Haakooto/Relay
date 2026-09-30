package com.relay.chainlang

/** Base type for anything that stops a chain expression from being accepted. */
sealed class ChainLangException(message: String) : Exception(message)

class ChainSyntaxException(message: String, val position: Int) :
    ChainLangException("Syntax error at $position: $message")

class UnknownReferenceException(val label: String) :
    ChainLangException("No saved timer or relay named \"$label\"")

/** A reference graph must be a DAG (spec §6.6) — this is the cycle path, source first. */
class CycleException(val cyclePath: List<String>) :
    ChainLangException("Reference cycle: ${cyclePath.joinToString(" → ")}")

class NestingTooDeepException(val depth: Int, val max: Int) :
    ChainLangException("Nesting depth $depth exceeds the max of $max")

class MultiplierTooLargeException(val requested: Long, val max: Int) :
    ChainLangException("repeat/range multiplier $requested exceeds the cap of $max")

class PlanTooLargeException(val projectedSize: Long, val max: Int) :
    ChainLangException("Resolved relay would have $projectedSize steps, over the limit of $max")

class ExpressionTooLongException(val length: Int, val max: Int) :
    ChainLangException("Expression is $length characters, over the limit of $max")

/** A step that works out to zero or negative time, e.g. `Test2 - 15` with a 10-minute Test2. */
class NonPositiveDurationException(val what: String, val seconds: Long) :
    ChainLangException("$what would last ${seconds}s; every step must be longer than 0")
