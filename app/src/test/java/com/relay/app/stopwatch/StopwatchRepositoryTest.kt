package com.relay.app.stopwatch

import com.relay.app.data.stopwatch.LapCorrectionEntity
import com.relay.app.data.stopwatch.LapEntity
import com.relay.app.data.stopwatch.StopwatchDao
import com.relay.app.data.stopwatch.StopwatchEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeStopwatchDao : StopwatchDao {
    val watches = MutableStateFlow<Map<String, StopwatchEntity>>(emptyMap())
    val laps = MutableStateFlow<Map<String, LapEntity>>(emptyMap())
    override fun observeAll(): Flow<List<StopwatchEntity>> = watches.map { it.values.sortedBy { w -> w.sortOrder } }
    override fun observeLaps(): Flow<List<LapEntity>> = laps.map { it.values.sortedBy { l -> l.number } }
    override suspend fun getAll() = watches.value.values.toList()
    override suspend fun get(id: String) = watches.value[id]
    override suspend fun laps(stopwatchId: String) = laps.value.values.filter { it.stopwatchId == stopwatchId }.sortedBy { it.number }
    override suspend fun upsert(stopwatch: StopwatchEntity) { watches.value += stopwatch.id to stopwatch }
    override suspend fun upsertLap(lap: LapEntity) { laps.value += lap.id to lap }
    override suspend fun delete(id: String) { watches.value -= id; laps.value = laps.value.filterValues { it.stopwatchId != id } }
    override suspend fun deleteLaps(stopwatchId: String) { laps.value = laps.value.filterValues { it.stopwatchId != stopwatchId } }
    override suspend fun setLapDetails(lapId: String, note: String, tags: String) { laps.value[lapId]?.let { laps.value += lapId to it.copy(note = note, tags = tags) } }
    override suspend fun deleteLap(lapId: String) { laps.value -= lapId }
    val corrections = MutableStateFlow<Map<String, LapCorrectionEntity>>(emptyMap())
    override fun observeCorrections(): Flow<List<LapCorrectionEntity>> = corrections.map { it.values.sortedBy { c -> c.seq } }
    override suspend fun corrections(stopwatchId: String) = corrections.value.values.filter { it.stopwatchId == stopwatchId }.sortedBy { it.seq }
    override suspend fun upsertCorrection(correction: LapCorrectionEntity) { corrections.value += correction.id to correction }
    override suspend fun dropUndone(stopwatchId: String) { corrections.value = corrections.value.filterValues { !(it.stopwatchId == stopwatchId && it.undone) } }
    override suspend fun deleteCorrections(stopwatchId: String) { corrections.value = corrections.value.filterValues { it.stopwatchId != stopwatchId } }
}

private class FakeNotices : StopwatchNotices {
    val showing = mutableSetOf<String>()
    override fun show(s: StopwatchEntity, lapCount: Int) { showing += s.id }
    override fun clear(id: String) { showing -= id }
}

class StopwatchRepositoryTest {
    private var clock = 1_000_000L
    private val dao = FakeStopwatchDao()
    private val notices = FakeNotices()
    private val repo = StopwatchRepository(dao, notices) { clock }

    private suspend fun view(id: String) = repo.observe().first().single { it.stopwatch.id == id }

    @Test fun startsRunningFromT0() = runBlocking {
        val id = repo.create("", t0Millis = 60_000)
        clock += 5_000
        assertEquals(65_000L, view(id).elapsed(clock))
        assertTrue(id in notices.showing)
    }

    @Test fun pauseFreezesResumeContinues() = runBlocking {
        val id = repo.create("", 0)
        clock += 10_000; repo.pause(id)
        clock += 50_000
        assertEquals(10_000L, view(id).elapsed(clock))
        assertTrue(id !in notices.showing)
        repo.resume(id); clock += 2_000
        assertEquals(12_000L, view(id).elapsed(clock))
    }

    /** The v0.5 bug: starting at 1m and lapping at +5 s must show a 0:05 lap, not 1:05. */
    @Test fun firstLapDeltaIsFromT0() = runBlocking {
        val id = repo.create("", t0Millis = 60_000)
        clock += 5_000; repo.lap(id)
        clock += 7_000; repo.lap(id)
        val laps = view(id).laps // newest first
        assertEquals(listOf(7_000L, 5_000L), laps.map { it.deltaMillis })
        assertEquals(listOf(72_000L, 65_000L), laps.map { it.lap.elapsedMillis })
    }

    @Test fun lapWhilePausedDoesNothing() = runBlocking {
        val id = repo.create("", 0)
        repo.pause(id); repo.lap(id)
        assertTrue(view(id).laps.isEmpty())
    }

    @Test fun restartGoesBackToT0AndClearsLaps() = runBlocking {
        val id = repo.create("", t0Millis = 30_000)
        clock += 10_000; repo.lap(id)
        repo.restart(id)
        assertEquals(30_000L, view(id).elapsed(clock))
        assertTrue(view(id).laps.isEmpty())
        assertTrue(view(id).running)
    }

    @Test fun lapNotesAreEditableTimesAreNot() = runBlocking {
        val id = repo.create("", 0)
        clock += 3_000; repo.lap(id)
        val lap = view(id).laps.single().lap
        repo.setLapDetails(lap.id, "  water break  ", emptyList())
        assertEquals("water break", view(id).laps.single().lap.note)
        assertEquals(3_000L, view(id).laps.single().lap.elapsedMillis)
    }

    @Test fun deleteRemovesNotification() = runBlocking {
        val id = repo.create("", 0)
        repo.delete(id)
        assertTrue(id !in notices.showing)
        assertTrue(repo.observe().first().isEmpty())
    }

    private suspend fun lapTimes(id: String) = view(id).laps.reversed().map { it.deltaMillis }

    private suspend fun threeLaps(): String {
        val id = repo.create("", 0, listOf("run", "break"))
        clock += 10_000; repo.lap(id, listOf("run"))
        clock += 20_000; repo.lap(id, listOf("break"))
        clock += 30_000; repo.lap(id)
        clock += 10_000
        return id
    }

    @Test fun tagsOnStopwatchAndLaps() = runBlocking {
        val id = threeLaps()
        assertEquals(listOf("run", "break"), view(id).tags)
        assertEquals(listOf(listOf("run"), listOf("break"), emptyList()), view(id).laps.reversed().map { it.tags })
    }

    @Test fun correctionsMarkLapsAndUndoRedo() = runBlocking {
        val id = threeLaps()
        val second = view(id).laps.reversed()[1].lap.id
        assertEquals(null, repo.adjustLap(id, second, -5_000, "late tap"))
        assertEquals(listOf(10_000L, 15_000L, 35_000L), lapTimes(id))
        assertEquals(listOf(false, true, true), view(id).laps.reversed().map { it.edited })
        assertTrue(view(id).canUndo)

        repo.undo(id)
        assertEquals(listOf(10_000L, 20_000L, 30_000L), lapTimes(id))
        assertEquals(listOf(false, false, false), view(id).laps.reversed().map { it.edited })
        assertTrue(view(id).canRedo)
        assertTrue(view(id).corrections.single().correction.undone) // still listed, greyed

        repo.redo(id)
        assertEquals(listOf(10_000L, 15_000L, 35_000L), lapTimes(id))
    }

    @Test fun newCorrectionDropsRedoBranch() = runBlocking {
        val id = threeLaps()
        val first = view(id).laps.reversed()[0].lap.id
        repo.adjustLap(id, first, 1_000, "")
        repo.undo(id)
        repo.removeLap(id, view(id).laps.reversed()[2].lap.id, "double tap")
        assertEquals(listOf(10_000L, 50_000L), lapTimes(id))
        assertEquals(1, view(id).corrections.size)
        assertEquals(false, view(id).canRedo)
    }

    @Test fun insertRenumbersAndRefusesBadInput() = runBlocking {
        val id = threeLaps()
        val second = view(id).laps.reversed()[1].lap.id
        assertEquals(EditError.OUT_OF_RANGE, repo.insertLap(id, second, 25_000, ""))
        assertEquals(null, repo.insertLap(id, second, 5_000, "missed a lap"))
        assertEquals(listOf(10_000L, 5_000L, 15_000L, 30_000L), lapTimes(id))
        assertEquals(listOf(1, 2, 3, 4), view(id).laps.reversed().map { it.lap.number })
    }

    @Test fun restartClearsHistory() = runBlocking {
        val id = threeLaps()
        repo.removeLap(id, view(id).laps.first().lap.id, "")
        repo.restart(id)
        assertTrue(view(id).corrections.isEmpty())
    }

    @Test fun statsOverFilteredLaps() = runBlocking {
        val id = threeLaps()
        val stats = lapStats(view(id).laps)!!
        assertEquals(3, stats.count)
        assertEquals(20_000L, stats.averageMillis)
        assertEquals(10_000L, stats.bestMillis)
        assertEquals(30_000L, stats.worstMillis)
        assertEquals(1, lapStats(view(id).laps.filter { "run" in it.tags })!!.count)
    }

    @Test fun negativeOffsetCountsUpThroughZero() = runBlocking {
        val id = repo.create("", t0Millis = -120_000)
        clock += 30_000
        assertEquals(-90_000L, view(id).elapsed(clock))
        repo.lap(id)
        clock += 120_000
        assertEquals(30_000L, view(id).elapsed(clock))
        assertEquals(30_000L, view(id).laps.single().deltaMillis) // first lap measured from t0
        repo.restart(id)
        assertEquals(-120_000L, view(id).elapsed(clock))
    }
}
