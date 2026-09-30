package com.relay.app.run

import com.relay.app.data.library.TransitionMode
import com.relay.app.data.library.TransitionTone
import com.relay.app.data.run.RunDao
import com.relay.app.data.run.RunEntity
import com.relay.app.data.run.RunKind
import com.relay.app.data.run.RunPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeRunDao : RunDao {
    val rows = MutableStateFlow<Map<String, RunEntity>>(emptyMap())
    override fun observeAll(): Flow<List<RunEntity>> = rows.map { it.values.toList() }
    override suspend fun getAll() = rows.value.values.toList()
    override suspend fun get(id: String) = rows.value[id]
    override suspend fun upsert(run: RunEntity) { rows.value += run.id to run }
    override suspend fun delete(id: String) { rows.value -= id }
}

private class FakeRunArming : RunArming {
    val armed = mutableMapOf<String, Pair<Int, Long>>()
    override fun armStepEnd(runId: String, stepIndex: Int, atMillis: Long) { armed[runId] = stepIndex to atMillis }
    override fun cancel(runId: String) { armed -= runId }
}

private class FakeRunNotices : RunNotices {
    val cancelledRings = mutableListOf<String>()
    override suspend fun show(view: RunView) {}
    override fun clear(runId: String) {}
    override fun cancelRing(runId: String) { cancelledRings += runId }
}

class RunRepositoryTest {
    private var clock = 1_000_000L
    private val dao = FakeRunDao()
    private val arming = FakeRunArming()
    private val notices = FakeRunNotices()
    private val repo = RunRepository(dao, arming, notices) { clock }

    private fun step(seconds: Long, after: TransitionMode = TransitionMode.SEAMLESS) =
        RunStep(null, seconds, null, true, after, TransitionTone.BEEP)

    private suspend fun start(vararg steps: RunStep) = repo.start(RunKind.RELAY, null, "", steps.toList()).run.id

    @Test fun startArmsFirstStepEnd() = runBlocking {
        val id = start(step(60))
        assertEquals(0 to clock + 60_000, arming.armed[id])
    }

    @Test fun seamlessAdvancesFromScheduledEndNotNow() = runBlocking {
        val id = start(step(60), step(30))
        val end = clock + 60_000
        clock = end + 5_000 // the alarm came 5 s late
        val decision = repo.onStepEnded(id, 0)
        assertTrue(decision is StepDecision.Advanced)
        assertEquals(1 to end + 30_000, arming.armed[id])
    }

    @Test fun interactionBoundaryAwaitsAndContinues() = runBlocking {
        val id = start(step(60, TransitionMode.INTERACTION), step(30))
        clock += 60_000
        assertTrue(repo.onStepEnded(id, 0) is StepDecision.Ring)
        assertEquals(RunPhase.AWAITING, dao.get(id)?.phase)
        assertNull(arming.armed[id])
        clock += 10_000
        repo.continueRun(id)
        assertEquals(1 to clock + 30_000, arming.armed[id])
    }

    @Test fun lastStepRingsDoneThenRestartAndAddMinute() = runBlocking {
        val id = start(step(60), step(30))
        clock += 60_000; repo.onStepEnded(id, 0)
        clock += 30_000
        assertTrue(repo.onStepEnded(id, 1) is StepDecision.Ring)
        assertEquals(RunPhase.DONE, dao.get(id)?.phase)
        repo.addMinute(id)
        assertEquals(1 to clock + 60_000, arming.armed[id])
        clock += 60_000; repo.onStepEnded(id, 1)
        repo.restart(id)
        assertEquals(0 to clock + 60_000, arming.armed[id])
    }

    @Test fun duplicateAndStaleAlarmsAreIgnored() = runBlocking {
        val id = start(step(60), step(30))
        clock += 60_000
        repo.onStepEnded(id, 0)
        assertEquals(StepDecision.Ignore, repo.onStepEnded(id, 0))
    }

    @Test fun earlyAlarmAfterAddMinuteIsIgnored() = runBlocking {
        val id = start(step(60))
        repo.addMinute(id)
        clock += 60_000 // the old end: an in-flight alarm from before +1 min
        assertEquals(StepDecision.Ignore, repo.onStepEnded(id, 0))
        assertEquals(RunPhase.RUNNING, dao.get(id)?.phase)
    }

    @Test fun pauseStopsTheClockResumeContinues() = runBlocking {
        val id = start(step(60))
        clock += 20_000
        repo.pause(id)
        assertNull(arming.armed[id])
        assertEquals(40_000L, dao.get(id)?.remainingMillis)
        clock += 100_000
        repo.resume(id)
        assertEquals(0 to clock + 40_000, arming.armed[id])
    }

    @Test fun resetGoesToStartPaused() = runBlocking {
        val id = start(step(60), step(30))
        clock += 60_000; repo.onStepEnded(id, 0)
        repo.reset(id)
        val run = dao.get(id)!!
        assertEquals(0, run.stepIndex)
        assertEquals(RunPhase.PAUSED, run.phase)
        assertEquals(60_000L, run.remainingMillis)
    }

    @Test fun stopDeletesAndSilences() = runBlocking {
        val id = start(step(60))
        repo.stop(id)
        assertNull(dao.get(id))
        assertNull(arming.armed[id])
        assertTrue(id in notices.cancelledRings)
    }

    @Test fun parallelRunsAreIndependent() = runBlocking {
        val a = start(step(60))
        clock += 10_000
        val b = start(step(60))
        repo.pause(a)
        assertNull(arming.armed[a])
        assertEquals(0 to clock + 60_000, arming.armed[b])
    }

    @Test fun rearmAllNeverChangesState() = runBlocking {
        val id = start(step(60), step(30))
        val before = dao.get(id)
        clock += 120_000 // process died past the step end
        repo.rearmAll()
        assertEquals(before, dao.get(id))
        assertTrue(repo.onStepEnded(id, 0) is StepDecision.Advanced) // the re-armed alarm still advances it
    }
}
