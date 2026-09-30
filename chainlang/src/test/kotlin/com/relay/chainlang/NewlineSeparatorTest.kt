package com.relay.chainlang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NewlineSeparatorTest {

    @Test
    fun `newline works as a separator instead of comma`() {
        val steps = ChainLang.run("(3\n10\n5)", NowClock(0, 0, 0))
        assertEquals(listOf(180L, 600L, 300L), steps.map { it.durationSeconds })
    }

    @Test
    fun `commas and newlines can be mixed freely`() {
        val steps = ChainLang.run("(3,\n10\n,5)", NowClock(0, 0, 0))
        assertEquals(listOf(180L, 600L, 300L), steps.map { it.durationSeconds })
    }

    @Test
    fun `blank lines and trailing separators are tolerated`() {
        val steps = ChainLang.run("(\n  3\n\n  10\n  5\n)", NowClock(0, 0, 0))
        assertEquals(listOf(180L, 600L, 300L), steps.map { it.durationSeconds })
    }

    @Test
    fun `newlines inside a function call are still just whitespace`() {
        val steps = ChainLang.run(
            "(repeat(2,\n  (3, 5)\n))",
            NowClock(0, 0, 0),
        )
        assertEquals(listOf(180L, 300L, 180L, 300L), steps.map { it.durationSeconds })
    }

    @Test
    fun `bare whitespace alone is still not a separator`() {
        assertFailsWith<ChainSyntaxException> { ChainLang.run("(3 10)", NowClock(0, 0, 0)) }
    }
}
