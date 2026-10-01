package com.relay.app.stopwatch

import com.relay.app.data.stopwatch.LapEntity
import com.relay.app.data.stopwatch.StopwatchDao
import com.relay.app.data.stopwatch.StopwatchEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** A stopwatch plus its laps, newest lap first, each with its delta. */
data class StopwatchView(val stopwatch: StopwatchEntity, val laps: List<LapView>) {
    val running: Boolean get() = stopwatch.startedAtMillis != null
    fun elapsed(now: Long): Long = elapsedOf(stopwatch, now)
}

data class LapView(val lap: LapEntity, val deltaMillis: Long)

fun elapsedOf(s: StopwatchEntity, now: Long): Long =
    s.t0Millis + s.accumulatedMillis + (s.startedAtMillis?.let { (now - it).coerceAtLeast(0) } ?: 0)

/** The first lap's delta is measured from t0, not from zero (a v0.5 bug, spec §07). */
fun lapViews(s: StopwatchEntity, laps: List<LapEntity>): List<LapView> {
    val sorted = laps.sortedBy { it.number }
    return sorted.mapIndexed { i, lap ->
        LapView(lap, lap.elapsedMillis - (if (i == 0) s.t0Millis else sorted[i - 1].elapsedMillis))
    }.reversed()
}

/** Notification side, as the repository needs it. Implemented by [StopwatchNotifications]. */
interface StopwatchNotices {
    fun show(s: StopwatchEntity, lapCount: Int)
    fun clear(id: String)
}

/** The command layer for stopwatches. One mutex serializes writes. */
class StopwatchRepository(
    private val dao: StopwatchDao,
    private val notices: StopwatchNotices,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    fun observe(): Flow<List<StopwatchView>> = combine(dao.observeAll(), dao.observeLaps()) { watches, laps ->
        val byWatch = laps.groupBy { it.stopwatchId }
        watches.map { StopwatchView(it, lapViews(it, byWatch[it.id].orEmpty())) }
    }

    /** Creates one and starts it straight away. */
    suspend fun create(name: String, t0Millis: Long): String = mutex.withLock {
        val t = now()
        val s = StopwatchEntity(UUID.randomUUID().toString(), name.trim(), t0Millis.coerceAtLeast(0), 0, t, t)
        dao.upsert(s)
        notices.show(s, 0)
        s.id
    }

    suspend fun pause(id: String) = change(id) { s ->
        if (s.startedAtMillis == null) null
        else s.copy(accumulatedMillis = s.accumulatedMillis + (now() - s.startedAtMillis).coerceAtLeast(0), startedAtMillis = null)
    }

    suspend fun resume(id: String) = change(id) { s -> if (s.startedAtMillis != null) null else s.copy(startedAtMillis = now()) }

    /** Back to t0, laps cleared; keeps running if it was. */
    suspend fun restart(id: String) = mutex.withLock {
        val s = dao.get(id) ?: return@withLock
        dao.deleteLaps(id)
        val updated = s.copy(accumulatedMillis = 0, startedAtMillis = if (s.startedAtMillis != null) now() else null)
        dao.upsert(updated)
        publish(updated, 0)
    }

    suspend fun lap(id: String) = mutex.withLock {
        val s = dao.get(id) ?: return@withLock
        if (s.startedAtMillis == null) return@withLock
        val laps = dao.laps(id)
        dao.upsertLap(LapEntity(UUID.randomUUID().toString(), id, laps.size + 1, elapsedOf(s, now()), ""))
        publish(s, laps.size + 1)
    }

    suspend fun rename(id: String, name: String) = change(id) { it.copy(name = name.trim()) }

    suspend fun setLapNote(lapId: String, note: String) = mutex.withLock { dao.setLapNote(lapId, note.trim()) }

    suspend fun delete(id: String) = mutex.withLock {
        notices.clear(id)
        dao.delete(id)
    }

    /** After a reboot or process start, re-post notifications for running stopwatches. */
    suspend fun republishAll() = mutex.withLock {
        for (s in dao.getAll()) publish(s, dao.laps(s.id).size)
    }

    private suspend fun change(id: String, transform: (StopwatchEntity) -> StopwatchEntity?) = mutex.withLock {
        val s = dao.get(id) ?: return@withLock
        val updated = transform(s) ?: return@withLock
        dao.upsert(updated)
        publish(updated, dao.laps(id).size)
    }

    private fun publish(s: StopwatchEntity, lapCount: Int) {
        if (s.startedAtMillis != null) notices.show(s, lapCount) else notices.clear(s.id)
    }
}
