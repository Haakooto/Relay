package com.relay.chainlang

import kotlin.test.Test
import kotlin.test.assertEquals

class ReferencedLabelsTest {

    @Test
    fun `plain durations have no references`() {
        assertEquals(emptySet(), ChainLang.referencedLabels("(3, 10, 5)"))
    }

    @Test
    fun `direct references are collected`() {
        assertEquals(setOf("Work", "Break"), ChainLang.referencedLabels("(Work, Break)"))
    }

    @Test
    fun `references inside repeat are still found`() {
        assertEquals(setOf("Work", "Break"), ChainLang.referencedLabels("(repeat(4, (Work, Break)))"))
    }

    @Test
    fun `Workday references Session and Work but not the anonymous gaps`() {
        assertEquals(setOf("Session", "Work"), ChainLang.referencedLabels("(Session, 30, Session, 60, Work)"))
    }
}
