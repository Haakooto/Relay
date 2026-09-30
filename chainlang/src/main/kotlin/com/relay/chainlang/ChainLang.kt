package com.relay.chainlang

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

    /** A single leaf duration, e.g. a TimerDef's own `durationExpr` — `"25m"`, `"2h30m10s"`, `"MINUTE(NOW)"`. */
    fun evalSingleDuration(source: String, now: NowClock, random: Random = Random.Default): Long {
        val wrapped = "($source)"
        val root = Parser.parse(wrapped)
        ChainSafety.validate(wrapped, root)
        return Evaluator(now, BlockResolver { throw UnknownReferenceException(it) }, random).evaluate(root).sumOf { it.durationSeconds }
    }

    /**
     * Every label a relay's content depends on directly, including names used in arithmetic
     * (`Test2 - 2` needs Test2's duration). What the store needs for cycle checks (spec §06).
     */
    fun referencedLabels(source: String): Set<String> = referencedLabels(Parser.parse(source))

    fun referencedLabels(node: ChainNode): Set<String> = when (node) {
        is Group -> node.elements.flatMap { referencedLabels(it) }.toSet()
        is Ref -> setOf(node.label)
        is RepeatCall -> referencedLabels(node.body)
        is DurationNode -> node.terms.mapNotNull { (it.term as? RefValueTerm)?.label }.toSet()
        is RangeCall -> emptySet()
    }

    /** Reserved words: never valid as a block name. */
    val KEYWORDS = setOf("repeat", "range", "arange", "RANDOM", "MINUTE", "HOUR", "SECOND", "NOW")

    /** Whether [label] can be referenced from a script: an identifier that isn't a keyword. */
    fun isReferenceableLabel(label: String): Boolean =
        label.isNotEmpty() && label[0].isLetter() && label.all { it.isLetterOrDigit() || it == '_' } && label !in KEYWORDS
}
