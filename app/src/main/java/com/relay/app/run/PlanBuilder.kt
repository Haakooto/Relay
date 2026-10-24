package com.relay.app.run

import com.relay.app.data.library.SoundSpec
import com.relay.app.data.library.StepOverride
import com.relay.app.data.library.TransitionMode
import com.relay.app.data.library.TransitionTone
import com.relay.chainlang.BlockRefStep
import com.relay.chainlang.BlockResolver
import com.relay.chainlang.ChainLang
import com.relay.chainlang.CycleException
import com.relay.chainlang.LeafStep
import com.relay.chainlang.NestingTooDeepException
import com.relay.chainlang.NowClock
import com.relay.chainlang.PlanTooLargeException
import com.relay.chainlang.ResolvedBlockInfo
import com.relay.chainlang.SafetyLimits
import com.relay.chainlang.UnknownReferenceException
import kotlin.random.Random

/** What the plan builder needs from a saved or draft timer. [warnSeconds] 0 = no warning. */
data class TimerSpec(
    val label: String,
    val durationExpr: String,
    val sound: SoundSpec,
    val warnSeconds: Long = 0,
    val warnTone: TransitionTone = TransitionTone.BEEP,
)

/** What the plan builder needs from a saved or draft relay. */
data class RelaySpec(
    val label: String,
    val script: String,
    val defaultSound: SoundSpec,
    val mode: TransitionMode,
    val tone: TransitionTone,
    val overrides: Map<Int, StepOverride> = emptyMap(),
)

/** The saved library, by label. Case-sensitive, as in scripts. */
interface Library {
    fun timer(label: String): TimerSpec?
    fun relay(label: String): RelaySpec?
}

enum class OwnStepKind { ANONYMOUS, TIMER, RELAY }

/** One of a relay's *own* steps, as the editor preview lists them (references not expanded). */
data class OwnStep(val index: Int, val label: String?, val seconds: Long, val kind: OwnStepKind)

/**
 * Expands a timer or relay into a flat [RunStep] plan (spec §06), following references into other
 * relays. This is the "plan check" of the spec: unlike chainlang's per-script shape check, it
 * counts steps and depth *across* references, and a cycle that slipped past save (a bad import,
 * say) fails as a [CycleException] instead of recursing forever.
 *
 * Pure Kotlin, no Android: unit-tested on the JVM.
 */
class PlanBuilder(
    private val library: Library,
    private val now: NowClock,
    private val limits: SafetyLimits = SafetyLimits(),
    /** The global default timer sound, for anything that says "default". */
    private val defaultSound: SoundSpec = SoundSpec(),
    private val random: Random = Random.Default,
) {
    private val totals = mutableMapOf<String, Long>()

    /**
     * One step, or two when the timer warns: the time until the warning, then the warning itself,
     * with a seamless cue between them (the same boundary relays use). A warning as long as the
     * timer, or longer, is skipped.
     */
    fun forTimer(timer: TimerSpec): List<RunStep> {
        val total = timerSeconds(timer)
        val step = RunStep(
            label = timer.label.ifBlank { null },
            seconds = total,
            soundUri = timer.sound.uri ?: defaultSound.uri,
            vibrate = timer.sound.vibrate,
            transitionAfter = TransitionMode.SEAMLESS,
            toneAfter = TransitionTone.SILENT,
        )
        val warn = timer.warnSeconds
        if (warn <= 0 || warn >= total) return listOf(step)
        return listOf(step.copy(seconds = total - warn, toneAfter = timer.warnTone), step.copy(seconds = warn))
    }

    fun forRelay(relay: RelaySpec): List<RunStep> {
        val out = mutableListOf<RunStep>()
        expand(relay, depth = 1, path = listOf(relay.label), out = out)
        return out
    }

    /** The relay's own steps, for the editor preview. Throws like [forRelay]. */
    fun ownSteps(relay: RelaySpec): List<OwnStep> =
        evaluate(relay, depth = 1, path = listOf(relay.label)).mapIndexed { i, step ->
            when (step) {
                is LeafStep -> OwnStep(i, step.label, step.durationSeconds, OwnStepKind.ANONYMOUS)
                is BlockRefStep -> OwnStep(
                    i, step.refLabel, step.durationSeconds,
                    if (library.relay(step.refLabel) != null) OwnStepKind.RELAY else OwnStepKind.TIMER,
                )
            }
        }

    fun timerSeconds(timer: TimerSpec): Long = ChainLang.evalSingleDuration(timer.durationExpr, now, random)

    private fun expand(relay: RelaySpec, depth: Int, path: List<String>, out: MutableList<RunStep>) {
        if (depth > limits.maxDepth) throw NestingTooDeepException(depth, limits.maxDepth)
        val steps = evaluate(relay, depth, path)
        steps.forEachIndexed { i, step ->
            val override = relay.overrides[i]
            val transition = override?.transition ?: relay.mode
            when (step) {
                is LeafStep -> add(
                    out,
                    RunStep(
                        label = step.label,
                        seconds = step.durationSeconds,
                        soundUri = override?.soundUri ?: step.soundProfile ?: relay.defaultSound.uri ?: defaultSound.uri,
                        vibrate = relay.defaultSound.vibrate,
                        transitionAfter = transition,
                        toneAfter = relay.tone,
                    ),
                )
                is BlockRefStep -> {
                    val subRelay = library.relay(step.refLabel)
                    if (subRelay != null) {
                        if (step.refLabel in path) throw CycleException(path + step.refLabel)
                        val start = out.size
                        expand(subRelay, depth + 1, path + step.refLabel, out)
                        // Leaving the referenced relay is a boundary the *parent* governs.
                        if (out.size > start) {
                            out[out.lastIndex] = out.last().copy(transitionAfter = transition, toneAfter = relay.tone)
                        }
                    } else {
                        val timer = library.timer(step.refLabel) ?: throw UnknownReferenceException(step.refLabel)
                        add(
                            out,
                            RunStep(
                                label = timer.label,
                                seconds = step.durationSeconds,
                                soundUri = override?.soundUri ?: timer.sound.uri ?: defaultSound.uri,
                                vibrate = timer.sound.vibrate,
                                transitionAfter = transition,
                                toneAfter = relay.tone,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun add(out: MutableList<RunStep>, step: RunStep) {
        if (out.size + 1 > limits.maxPlanSize) throw PlanTooLargeException(out.size + 1L, limits.maxPlanSize)
        out += step
    }

    private fun evaluate(relay: RelaySpec, depth: Int, path: List<String>) =
        ChainLang.run(relay.script, now, resolver(depth, path), random, defaultSoundProfile = null, limits = limits)

    /** Durations of referenced blocks. A relay's total needs its own expansion, hence the guards. */
    private fun resolver(depth: Int, path: List<String>) = BlockResolver { label ->
        library.timer(label)?.let { return@BlockResolver ResolvedBlockInfo(timerSeconds(it), it.sound.uri, isChain = false) }
        val relay = library.relay(label) ?: throw UnknownReferenceException(label)
        if (label in path) throw CycleException(path + label)
        if (depth + 1 > limits.maxDepth) throw NestingTooDeepException(depth + 1, limits.maxDepth)
        val total = totals.getOrPut(label) {
            val out = mutableListOf<RunStep>()
            expand(relay, depth + 1, path + label, out)
            out.sumOf { it.seconds }
        }
        ResolvedBlockInfo(total, relay.defaultSound.uri, isChain = true)
    }
}
