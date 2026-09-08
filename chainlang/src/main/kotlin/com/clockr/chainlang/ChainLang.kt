package com.clockr.chainlang

import kotlin.random.Random

/** Public entry point — parse, run the safety hedge, then (optionally) evaluate. */
object ChainLang {

    /** @throws ChainLangException if the expression is malformed or trips a safety limit. */
    fun parseAndValidate(source: String, limits: SafetyLimits = SafetyLimits()): Group {
        val root = Parser.parse(source)
        ChainSafety.validate(source, root, limits)
        return root
    }

    fun run(
        source: String,
        now: NowClock,
        resolver: BlockResolver = BlockResolver { throw UnknownReferenceException(it) },
        random: Random = Random.Default,
        defaultSoundProfile: String? = null,
        limits: SafetyLimits = SafetyLimits(),
    ): List<ResolvedStep> {
        val root = parseAndValidate(source, limits)
        return Evaluator(now, resolver, random, defaultSoundProfile).evaluate(root)
    }
}
