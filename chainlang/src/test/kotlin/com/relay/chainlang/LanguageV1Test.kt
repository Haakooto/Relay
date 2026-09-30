package com.relay.chainlang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The v1.0 spec's language additions: ref arithmetic, fractional range, step labels. */
class LanguageV1Test {
    private val noon = NowClock(12, 0, 0)
    private val resolver = BlockResolver { label ->
        when (label) {
            "Test2" -> ResolvedBlockInfo(10 * 60L, "bell", isChain = false)
            "Break" -> ResolvedBlockInfo(5 * 60L, "chime", isChain = false)
            "Session" -> ResolvedBlockInfo(2 * 3600L, null, isChain = true)
            else -> throw UnknownReferenceException(label)
        }
    }

    private fun run(source: String) = ChainLang.run(source, noon, resolver)

    @Test fun `name minus minutes is one anonymous step`() {
        val step = run("(Test2 - 2)").single()
        assertTrue(step is LeafStep)
        assertEquals(8 * 60L, step.durationSeconds)
    }

    @Test fun `ref arithmetic inherits the single referenced sound`() {
        assertEquals("bell", (run("(Test2 - 2)").single() as LeafStep).soundProfile)
    }

    @Test fun `two names in arithmetic fall back to the default sound`() {
        assertNull((run("(Test2 + Break)").single() as LeafStep).soundProfile)
    }

    @Test fun `ref arithmetic mixes with literals`() {
        assertEquals(10 * 60L + 30, run("(Test2 + 30s)").single().durationSeconds)
    }

    @Test fun `a relay name in arithmetic contributes its total`() {
        assertEquals(2 * 3600L - 10 * 60L, run("(Session - 10)").single().durationSeconds)
    }

    @Test fun `bare name stays a reference step`() {
        assertTrue(run("(Test2)").single() is BlockRefStep)
    }

    @Test fun `zero or negative step is rejected`() {
        assertFailsWith<NonPositiveDurationException> { run("(Test2 - 10)") }
        assertFailsWith<NonPositiveDurationException> { run("(Test2 - 15)") }
        assertFailsWith<NonPositiveDurationException> { run("(range(0, 3))") }
    }

    @Test fun `fractional range steps`() {
        assertEquals(listOf(60L, 90L, 120L, 150L), run("(range(1, 3, 0.5))").map { it.durationSeconds })
    }

    @Test fun `fractional range down`() {
        assertEquals(listOf(90L, 60L), run("(range(1.5, 0.5, 0.5))").map { it.durationSeconds })
    }

    @Test fun `tenth-minute steps don't drift`() {
        assertEquals(10, run("(range(1, 2, 0.1))").size)
    }

    @Test fun `arange is gone with a helpful message`() {
        val e = assertFailsWith<ChainSyntaxException> { run("(arange(1, 3))") }
        assertTrue("range" in e.message!!)
    }

    @Test fun `decimals outside range are rejected`() {
        assertFailsWith<ChainSyntaxException> { run("(1.5)") }
        assertFailsWith<ChainSyntaxException> { run("(repeat(1.5, 1))") }
    }

    @Test fun `anonymous steps carry labels`() {
        val steps = run("(5m \"stretch\", 25, Test2 - 5 \"short\")")
        assertEquals(listOf("stretch", null, "short"), steps.map { (it as LeafStep).label })
    }

    @Test fun `a label on a reference is rejected`() {
        assertFailsWith<ChainSyntaxException> { run("(Test2 \"x\")") }
    }

    @Test fun `unclosed label is a syntax error`() {
        assertFailsWith<ChainSyntaxException> { run("(5m \"stretch)") }
    }

    @Test fun `arithmetic names count as references for cycle checks`() {
        assertEquals(setOf("Test2", "Work"), ChainLang.referencedLabels("(Test2 - 2, Work)"))
    }

    @Test fun `fractional step can't sneak past the multiplier cap`() {
        assertFailsWith<MultiplierTooLargeException> { ChainLang.parseAndValidate("(range(0, 100, 0.25))") }
    }

    @Test fun `referenceable labels`() {
        assertTrue(ChainLang.isReferenceableLabel("Test2"))
        assertTrue(ChainLang.isReferenceableLabel("Økt_1"))
        assertEquals(false, ChainLang.isReferenceableLabel("Take pills"))
        assertEquals(false, ChainLang.isReferenceableLabel("2fast"))
        assertEquals(false, ChainLang.isReferenceableLabel("repeat"))
    }

    @Test fun `old arithmetic still works`() {
        assertEquals(35 * 60L, run("(30m + 5)").single().durationSeconds)
        assertEquals(2 * 60L, run("(3 - 1)").single().durationSeconds)
    }
}
