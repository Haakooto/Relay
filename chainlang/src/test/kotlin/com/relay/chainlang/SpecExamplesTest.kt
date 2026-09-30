package com.relay.chainlang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** One test per worked example from spec §6.4/§6.5, so a spec change shows up here directly. */
class SpecExamplesTest {

    private val noon = NowClock(hour = 14, minute = 37, second = 0)

    private fun run(source: String, now: NowClock = noon, resolver: BlockResolver = BlockResolver { throw UnknownReferenceException(it) }) =
        ChainLang.run(source, now, resolver)

    @Test
    fun `plain list of durations`() {
        val steps = run("(3, 10, 5)")
        assertEquals(listOf(180L, 600L, 300L), steps.map { it.durationSeconds })
    }

    @Test
    fun `arange counts down`() {
        val steps = run("(arange(20, 0, 5))")
        assertEquals(listOf(20L, 15L, 10L, 5L).map { it * 60 }, steps.map { it.durationSeconds })
    }

    @Test
    fun `nested repeat of arange`() {
        val steps = run("(3, repeat(3, arange(10,0,5)))")
        assertEquals(listOf(3, 10, 5, 10, 5, 10, 5).map { it * 60L }, steps.map { it.durationSeconds })
    }

    @Test
    fun `explicit hms literal`() {
        val steps = run("(2h30m10s)")
        assertEquals(listOf(2 * 3600L + 30 * 60L + 10L), steps.map { it.durationSeconds })
    }

    @Test
    fun `bare number means minutes, same as explicit hours`() {
        assertEquals(run("(120)").single().durationSeconds, run("(2h)").single().durationSeconds)
    }

    @Test
    fun `MINUTE of NOW`() {
        val steps = run("(MINUTE(NOW))")
        assertEquals(37 * 60L, steps.single().durationSeconds)
    }

    @Test
    fun `MINUTE of NOW plus offset rolls the hour`() {
        val steps = run("(MINUTE(NOW+30m))")
        assertEquals(7 * 60L, steps.single().durationSeconds)
    }

    @Test
    fun `arithmetic applies outside the time function, not inside`() {
        val steps = run("(MINUTE(NOW) + 30)")
        assertEquals(67 * 60L, steps.single().durationSeconds)
    }

    @Test
    fun `Session references Work and Break by label`() {
        val resolver = BlockResolver { label ->
            when (label) {
                "Work" -> ResolvedBlockInfo(25 * 60L, "sadsong", isChain = false)
                "Break" -> ResolvedBlockInfo(5 * 60L, "happysong", isChain = false)
                else -> throw UnknownReferenceException(label)
            }
        }
        val steps = run("(repeat(4, (Work, Break)))", resolver = resolver)
        assertEquals(2 * 3600L, steps.sumOf { it.durationSeconds }) // 2h total, matches spec table
        val labels = steps.map { (it as BlockRefStep).refLabel }
        assertEquals(List(4) { listOf("Work", "Break") }.flatten(), labels)
    }

    @Test
    fun `Workday totals 5h55m across nested chain references`() {
        val resolver = BlockResolver { label ->
            when (label) {
                "Session" -> ResolvedBlockInfo(2 * 3600L, null, isChain = true)
                "Work" -> ResolvedBlockInfo(25 * 60L, "sadsong", isChain = false)
                else -> throw UnknownReferenceException(label)
            }
        }
        val steps = run("(Session, 30, Session, 60, Work)", resolver = resolver)
        assertEquals(5 * 3600L + 55 * 60L, steps.sumOf { it.durationSeconds })
        // A chain reference is one opaque step, never flattened into its own sub-steps.
        assertEquals(true, steps[0] is BlockRefStep)
        assertEquals("Session", (steps[0] as BlockRefStep).refLabel)
    }

    @Test
    fun `repeat above the 200 cap is rejected`() {
        assertFailsWith<MultiplierTooLargeException> { run("(repeat(201, 1))") }
    }

    @Test
    fun `repeat at exactly the 200 cap is fine`() {
        val steps = run("(repeat(200, 1))")
        assertEquals(200, steps.size)
    }

    @Test
    fun `unknown reference surfaces the label`() {
        val ex = assertFailsWith<UnknownReferenceException> { run("(Nonexistent)") }
        assertEquals("Nonexistent", ex.label)
    }
}
