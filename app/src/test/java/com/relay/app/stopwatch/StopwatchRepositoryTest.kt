package com.relay.app.stopwatch

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
    override suspend fun setLapNote(lapId: String, note: String) { laps.value[lapId]?.let { laps.value += lapId to it.copy(note = note) } }
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
        repo.setLapNote(lap.id, "  water break  ")
        assertEquals("water break", view(id).laps.single().lap.note)
        assertEquals(3_000L, view(id).laps.single().lap.elapsedMillis)
    }

    @Test fun deleteRemovesNotification() = runBlocking {
        val id = repo.create("", 0)
        repo.delete(id)
        assertTrue(id !in notices.showing)
        assertTrue(repo.observe().first().isEmpty())
    }
}
