package com.relay.chainlang

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SafetyTest {

    @Test
    fun `plan size at the default cap is accepted`() {
        // repeat(150, (0,1)) -> 300 leaf steps, exactly the default cap.
        val root = Parser.parse("(repeat(150, (0, 1)))")
        ChainSafety.validate("(repeat(150, (0, 1)))", root)
    }

    @Test
    fun `plan size one over the default cap is rejected`() {
        val source = "(repeat(150, (0, 1, 2)))" // 150 * 3 = 450
        val root = Parser.parse(source)
        assertFailsWith<PlanTooLargeException> { ChainSafety.validate(source, root) }
    }

    @Test
    fun `nesting bomb is rejected before it would ever be materialized`() {
        // 200^3 would be 8,000,000 leaf steps if actually expanded — each individual repeat(200, …)
        // is within the per-call cap, so this is caught by the projected *plan size* going over
        // 300 (already true two levels down, long before the outer repeat is even considered),
        // never by materializing anything.
        val source = "(repeat(200, repeat(200, repeat(200, 1))))"
        val root = Parser.parse(source)
        assertFailsWith<PlanTooLargeException> { ChainSafety.validate(source, root) }
    }

    @Test
    fun `depth beyond the max is rejected`() {
        val nested = (1..25).fold("1") { acc, _ -> "($acc)" }
        val source = "($nested)"
        assertFailsWith<NestingTooDeepException> {
            val root = Parser.parse(source)
            ChainSafety.validate(source, root)
        }
    }

    @Test
    fun `oversized expression text is rejected`() {
        val source = "(" + "1, ".repeat(1000) + "1)"
        assertTrue(source.length > SafetyLimits().maxExpressionLength)
        val root = Parser.parse(source)
        assertFailsWith<ExpressionTooLongException> { ChainSafety.validate(source, root) }
    }
}
