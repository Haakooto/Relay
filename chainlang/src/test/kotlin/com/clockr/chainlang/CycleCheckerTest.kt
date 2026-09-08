package com.clockr.chainlang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CycleCheckerTest {

    @Test
    fun `Work, Break, Session, Workday from the spec example has no cycle`() {
        val graph = mapOf(
            "Work" to emptyList(),
            "Break" to emptyList(),
            "Session" to listOf("Work", "Break"),
            "Workday" to listOf("Session", "Work"),
        )
        CycleChecker.checkAcyclic(graph.keys) { graph.getValue(it) }
    }

    @Test
    fun `direct self-reference is a cycle`() {
        val graph = mapOf("Work" to listOf("Work"))
        assertFailsWith<CycleException> {
            CycleChecker.checkAcyclic(graph.keys) { graph.getValue(it) }
        }
    }

    @Test
    fun `indirect cycle reports the full path`() {
        // Workday -> Session -> Workday, exactly the example named in spec §6.6.
        val graph = mapOf(
            "Session" to listOf("Workday"),
            "Workday" to listOf("Session"),
        )
        val ex = assertFailsWith<CycleException> {
            CycleChecker.checkAcyclic(graph.keys) { graph.getValue(it) }
        }
        assertEquals(listOf("Session", "Workday", "Session"), ex.cyclePath)
    }
}
