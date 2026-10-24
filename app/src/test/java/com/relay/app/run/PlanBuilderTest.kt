package com.relay.app.run

import com.relay.app.data.library.SoundSpec
import com.relay.app.data.library.StepOverride
import com.relay.app.data.library.TransitionMode
import com.relay.app.data.library.TransitionTone
import com.relay.chainlang.CycleException
import com.relay.chainlang.NowClock
import com.relay.chainlang.PlanTooLargeException
import com.relay.chainlang.SafetyLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlanBuilderTest {
    private val timers = mutableMapOf<String, TimerSpec>()
    private val relays = mutableMapOf<String, RelaySpec>()
    private val library = object : Library {
        override fun timer(label: String) = timers[label]
        override fun relay(label: String) = relays[label]
    }

    private fun relay(label: String, script: String, mode: TransitionMode = TransitionMode.SEAMLESS, sound: String? = null,
                      overrides: Map<Int, StepOverride> = emptyMap()) =
        RelaySpec(label, script, SoundSpec(sound), mode, TransitionTone.BEEP, overrides).also { relays[label] = it }

    private fun builder(limits: SafetyLimits = SafetyLimits()) =
        PlanBuilder(library, NowClock(12, 0, 0), limits, SoundSpec("global"))

    init {
        timers["Work"] = TimerSpec("Work", "25m", SoundSpec("sadsong"))
        timers["Break"] = TimerSpec("Break", "5m", SoundSpec("happysong"))
    }

    @Test fun specWorkdayIs5h55m() {
        relay("Session", "(repeat(4, (Work, Break)))")
        val workday = relay("Workday", "(Session, 30, Session, 60, Work)", sound = "joysong")
        val plan = builder().forRelay(workday)
        assertEquals(5 * 3600L + 55 * 60, plan.sumOf { it.seconds })
        assertEquals(8 + 1 + 8 + 1 + 1, plan.size)
    }

    @Test fun soundsFollowTheirSource() {
        val r = relay("R", "(Work, 10, Break - 1)", sound = "relaydefault")
        val plan = builder().forRelay(r)
        assertEquals(listOf("sadsong", "relaydefault", "happysong"), plan.map { it.soundUri })
    }

    @Test fun defaultsFallBackToGlobal() {
        timers["Plain"] = TimerSpec("Plain", "1m", SoundSpec(null))
        val plan = builder().forRelay(relay("R", "(Plain, 2)"))
        assertEquals(listOf("global", "global"), plan.map { it.soundUri })
    }

    @Test fun nestedRelayLastBoundaryBelongsToParent() {
        relay("Inner", "(1, 2)", mode = TransitionMode.SEAMLESS)
        val outer = relay("Outer", "(Inner, 3)", mode = TransitionMode.INTERACTION)
        val plan = builder().forRelay(outer)
        assertEquals(
            listOf(TransitionMode.SEAMLESS, TransitionMode.INTERACTION, TransitionMode.INTERACTION),
            plan.map { it.transitionAfter },
        )
    }

    @Test fun overridesApplyToOwnSteps() {
        val r = relay(
            "R", "(Work, Break, Work)",
            overrides = mapOf(0 to StepOverride(transition = TransitionMode.INTERACTION), 1 to StepOverride(soundUri = "special")),
        )
        val plan = builder().forRelay(r)
        assertEquals(listOf(TransitionMode.INTERACTION, TransitionMode.SEAMLESS, TransitionMode.SEAMLESS), plan.map { it.transitionAfter })
        assertEquals("special", plan[1].soundUri)
    }

    @Test fun planSizeCountsAcrossReferences() {
        relay("Big", "(repeat(100, 1))")
        val r = relay("R", "(repeat(4, Big))") // 4 own steps, 400 expanded
        assertThrows(PlanTooLargeException::class.java) { builder().forRelay(r) }
    }

    @Test fun cycleThatSlippedPastSaveFailsSafely() {
        relay("A", "(B)")
        val b = relay("B", "(A)")
        assertThrows(CycleException::class.java) { builder().forRelay(b) }
    }

    @Test fun selfReferenceFails() {
        val a = relay("A", "(A, 1)")
        assertThrows(CycleException::class.java) { builder().forRelay(a) }
    }

    @Test fun ownStepsForPreview() {
        relay("Session", "(Work, Break)")
        val steps = builder().ownSteps(relay("R", "(Session, 5 \"stretch\", Work)"))
        assertEquals(listOf(OwnStepKind.RELAY, OwnStepKind.ANONYMOUS, OwnStepKind.TIMER), steps.map { it.kind })
        assertEquals("stretch", steps[1].label)
        assertEquals(30 * 60L, steps[0].seconds)
    }

    @Test fun timerPlanIsOneStep() {
        val plan = builder().forTimer(TimerSpec("Tea", "4m", SoundSpec(null, vibrate = false)))
        assertEquals(1, plan.size)
        assertEquals(240L, plan[0].seconds)
        assertEquals("global", plan[0].soundUri)
        assertEquals(false, plan[0].vibrate)
    }

    @Test fun warnedTimerSplitsBeforeTheEnd() {
        val plan = builder().forTimer(TimerSpec("Pasta", "20m", SoundSpec(null), warnSeconds = 60, warnTone = TransitionTone.CHIME))
        assertEquals(listOf(19 * 60L, 60L), plan.map { it.seconds })
        assertEquals(TransitionMode.SEAMLESS, plan[0].transitionAfter)
        assertEquals(TransitionTone.CHIME, plan[0].toneAfter)
        assertEquals(listOf("Pasta", "Pasta"), plan.map { it.label })
        assertEquals(listOf("global", "global"), plan.map { it.soundUri })
    }

    @Test fun warningAsLongAsTheTimerIsSkipped() {
        assertEquals(1, builder().forTimer(TimerSpec("Egg", "1m", SoundSpec(null), warnSeconds = 60)).size)
        assertEquals(1, builder().forTimer(TimerSpec("Egg", "1m", SoundSpec(null), warnSeconds = 0)).size)
    }

    @Test fun warnedTimerInsideARelayIsOneStep() {
        timers["Warned"] = TimerSpec("Warned", "10m", SoundSpec(null), warnSeconds = 60)
        val plan = builder().forRelay(relay("Uses", "(Warned, 5m)"))
        assertEquals(listOf(600L, 300L), plan.map { it.seconds })
    }
}
