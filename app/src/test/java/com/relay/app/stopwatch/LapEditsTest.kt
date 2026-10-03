package com.relay.app.stopwatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LapEditsTest {
    // Laps of 10 s, 20 s, 30 s after a t0 of 0: boundaries 10, 30, 60 s. Running total 70 s.
    private val laps = listOf(LapSnap("a", 10_000, "warm-up", listOf("run")), LapSnap("b", 30_000, "fast"), LapSnap("c", 60_000))
    private fun durations(l: List<LapSnap>, t0: Long = 0) = l.mapIndexed { i, s -> s.elapsedMillis - (if (i == 0) t0 else l[i - 1].elapsedMillis) }
    private fun applyEdit(r: Result<LapEdit>) = LapEdits.apply(laps, r.getOrThrow().ops)

    @Test fun removeMergesIntoPreviousKeepingItsDetails() {
        val out = applyEdit(LapEdits.remove(laps, "b"))
        assertEquals(listOf(30_000L, 30_000L), durations(out))
        assertEquals("warm-up", out[0].note)
        assertEquals(listOf("run"), out[0].tags)
    }

    @Test fun removingLapOneMergesIntoLapTwo() {
        val out = applyEdit(LapEdits.remove(laps, "a"))
        assertEquals(listOf(30_000L, 30_000L), durations(out))
        assertEquals("fast", out[0].note)
    }

    @Test fun insertSplitsALap() {
        // Lap 2 was 20 s; split 5 s in: 5 s then 15 s, later laps renumber.
        val out = applyEdit(LapEdits.insert(laps, 0, "b", 5_000, newId = "n"))
        assertEquals(listOf(10_000L, 5_000L, 15_000L, 30_000L), durations(out))
        assertEquals("fast", out[1].note) // the first part keeps the lap's details
        assertEquals("", out[2].note)
    }

    @Test fun insertMustLandInsideTheLap() {
        assertTrue(LapEdits.insert(laps, 0, "b", 20_000).isFailure)
        assertTrue(LapEdits.insert(laps, 0, "b", 0).isFailure)
    }

    @Test fun adjustMovesOneBoundary() {
        val out = applyEdit(LapEdits.adjust(laps, 0, "b", -5_000, 70_000))
        assertEquals(listOf(10_000L, 15_000L, 35_000L), durations(out))
        assertEquals(60_000L, out.last().elapsedMillis) // total untouched
    }

    @Test fun adjustCantMakeALapNegative() {
        assertTrue(LapEdits.adjust(laps, 0, "b", -21_000, 70_000).isFailure)
        assertTrue(LapEdits.adjust(laps, 0, "b", 31_000, 70_000).isFailure)
        assertTrue(LapEdits.adjust(laps, 0, "c", 11_000, 70_000).isFailure) // past the running time
        assertTrue(LapEdits.adjust(laps, 0, "b", -20_000, 70_000).isSuccess) // exactly zero is allowed
    }

    @Test fun firstLapIsBoundedByT0() {
        assertTrue(LapEdits.adjust(laps, 5_000, "a", -6_000, 70_000).isFailure)
    }

    @Test fun undoRestoresExactly() {
        for (edit in listOf(LapEdits.remove(laps, "b"), LapEdits.insert(laps, 0, "c", 7_000, "n"), LapEdits.adjust(laps, 0, "a", 3_000, 70_000))) {
            val ops = edit.getOrThrow().ops
            assertEquals(laps, LapEdits.apply(LapEdits.apply(laps, ops), LapEdits.undoOps(ops)))
        }
    }

    @Test fun undoStillWorksAfterANewLap() {
        val ops = LapEdits.remove(laps, "b").getOrThrow().ops
        val withNewLap = LapEdits.apply(laps, ops) + LapSnap("d", 80_000)
        val undone = LapEdits.apply(withNewLap, LapEdits.undoOps(ops))
        assertEquals(laps + LapSnap("d", 80_000), undone)
    }

    @Test fun opsSurviveJson() {
        val ops = LapEdits.insert(laps, 0, "b", 5_000, "n").getOrThrow().ops
        assertEquals(ops, LapEdits.decode(LapEdits.encode(ops)))
    }

    @Test fun amounts() {
        assertEquals(5_000L, LapEdits.parseAmount("5"))
        assertEquals(5_000L, LapEdits.parseAmount("5s"))
        assertEquals(300_000L, LapEdits.parseAmount("5m"))
        assertEquals(300_000L, LapEdits.parseAmount("5 m"))
        assertNull(LapEdits.parseAmount("5min"))
        assertEquals(-5_000L, LapEdits.parseAmount("-5s"))
        assertEquals(120_000L, LapEdits.parseAmount("+2m"))
        assertEquals(90_000L, LapEdits.parseAmount("1m30s"))
        assertEquals(90_000L, LapEdits.parseAmount("1:30"))
        assertEquals(1_500L, LapEdits.parseAmount("1.5s"))
        assertEquals(1_500L, LapEdits.parseAmount("1,5 s"))
        assertEquals(3_600_000L, LapEdits.parseAmount("1h"))
        assertNull(LapEdits.parseAmount("abc"))
        assertNull(LapEdits.parseAmount("1:75"))
        assertNull(LapEdits.parseAmount(""))
    }
}
