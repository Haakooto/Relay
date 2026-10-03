package com.relay.app.stopwatch

import com.relay.app.data.stopwatch.LapCorrectionEntity
import com.relay.app.data.stopwatch.LapEntity
import com.relay.app.data.stopwatch.StopwatchDao
import com.relay.app.data.stopwatch.StopwatchEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** A stopwatch, its tags, its laps (newest first) and its correction history. */
data class StopwatchView(
    val stopwatch: StopwatchEntity,
    val tags: List<String>,
    val laps: List<LapView>,
    val corrections: List<CorrectionView>,
) {
    val running: Boolean get() = stopwatch.startedAtMillis != null
    fun elapsed(now: Long): Long = elapsedOf(stopwatch, now)
    val canUndo: Boolean get() = corrections.any { !it.correction.undone }
    val canRedo: Boolean get() = corrections.any { it.correction.undone }
}

data class LapView(val lap: LapEntity, val deltaMillis: Long, val tags: List<String>, val edited: Boolean)

data class CorrectionView(val correction: LapCorrectionEntity, val kind: CorrectionKind)

fun elapsedOf(s: StopwatchEntity, now: Long): Long =
    s.t0Millis + s.accumulatedMillis + (s.startedAtMillis?.let { (now - it).coerceAtLeast(0) } ?: 0)

private val json = Json { ignoreUnknownKeys = true }
fun decodeTags(text: String): List<String> = runCatching { json.decodeFromString<List<String>>(text) }.getOrDefault(emptyList())
fun encodeTags(tags: List<String>): String = json.encodeToString(tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct())

/** The first lap's delta is measured from t0, not from zero (a v0.5 bug, spec §07). */
fun lapViews(s: StopwatchEntity, laps: List<LapEntity>, edited: Set<String> = emptySet()): List<LapView> {
    val sorted = laps.sortedBy { it.elapsedMillis }
    return sorted.mapIndexed { i, lap ->
        LapView(lap, lap.elapsedMillis - (if (i == 0) s.t0Millis else sorted[i - 1].elapsedMillis), decodeTags(lap.tags), lap.id in edited)
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

    fun observe(): Flow<List<StopwatchView>> = combine(dao.observeAll(), dao.observeLaps(), dao.observeCorrections()) { watches, laps, corrections ->
        val lapsBy = laps.groupBy { it.stopwatchId }
        val correctionsBy = corrections.groupBy { it.stopwatchId }
        watches.map { s ->
            val own = correctionsBy[s.id].orEmpty()
            val edited = own.filterNot { it.undone }.flatMap { decodeTags(it.affected) }.toSet()
            StopwatchView(
                s, decodeTags(s.tags), lapViews(s, lapsBy[s.id].orEmpty(), edited),
                own.map { CorrectionView(it, runCatching { CorrectionKind.valueOf(it.kind) }.getOrDefault(CorrectionKind.ADJUST)) },
            )
        }
    }

    /** Creates one and starts it straight away. A negative [t0Millis] counts up through zero. */
    suspend fun create(name: String, t0Millis: Long, tags: List<String> = emptyList()): String = mutex.withLock {
        val t = now()
        val s = StopwatchEntity(UUID.randomUUID().toString(), name.trim(), t0Millis, 0, t, t, encodeTags(tags))
        dao.upsert(s)
        notices.show(s, 0)
        s.id
    }

    suspend fun pause(id: String) = change(id) { s ->
        if (s.startedAtMillis == null) null
        else s.copy(accumulatedMillis = s.accumulatedMillis + (now() - s.startedAtMillis).coerceAtLeast(0), startedAtMillis = null)
    }

    suspend fun resume(id: String) = change(id) { s -> if (s.startedAtMillis != null) null else s.copy(startedAtMillis = now()) }

    /** Back to t0, laps and corrections cleared; keeps running if it was. */
    suspend fun restart(id: String) = mutex.withLock {
        val s = dao.get(id) ?: return@withLock
        dao.deleteLaps(id)
        dao.deleteCorrections(id)
        val updated = s.copy(accumulatedMillis = 0, startedAtMillis = if (s.startedAtMillis != null) now() else null)
        dao.upsert(updated)
        publish(updated, 0)
    }

    /** A lap, optionally tagged straight away (the per-tag lap buttons). */
    suspend fun lap(id: String, tags: List<String> = emptyList()) = mutex.withLock {
        val s = dao.get(id) ?: return@withLock
        if (s.startedAtMillis == null) return@withLock
        val laps = dao.laps(id)
        dao.upsertLap(LapEntity(UUID.randomUUID().toString(), id, laps.size + 1, elapsedOf(s, now()), "", encodeTags(tags)))
        publish(s, laps.size + 1)
    }

    suspend fun updateDetails(id: String, name: String, tags: List<String>) = change(id) { it.copy(name = name.trim(), tags = encodeTags(tags)) }

    suspend fun setLapDetails(lapId: String, note: String, tags: List<String>) = mutex.withLock { dao.setLapDetails(lapId, note.trim(), encodeTags(tags)) }

    suspend fun delete(id: String) = mutex.withLock {
        notices.clear(id)
        dao.delete(id)
    }

    // --- corrections ----------------------------------------------------------------------

    suspend fun removeLap(stopwatchId: String, lapId: String, label: String): EditError? =
        correct(stopwatchId, lapId, CorrectionKind.REMOVE, 0, label) { laps, _, _ -> LapEdits.remove(laps, lapId) }

    suspend fun insertLap(stopwatchId: String, lapId: String, intoMillis: Long, label: String): EditError? =
        correct(stopwatchId, lapId, CorrectionKind.INSERT, intoMillis, label) { laps, s, _ -> LapEdits.insert(laps, s.t0Millis, lapId, intoMillis) }

    suspend fun adjustLap(stopwatchId: String, lapId: String, deltaMillis: Long, label: String): EditError? =
        correct(stopwatchId, lapId, CorrectionKind.ADJUST, deltaMillis, label) { laps, s, t -> LapEdits.adjust(laps, s.t0Millis, lapId, deltaMillis, elapsedOf(s, t)) }

    /** Undoes the latest active correction. */
    suspend fun undo(stopwatchId: String) = mutex.withLock {
        val c = dao.corrections(stopwatchId).lastOrNull { !it.undone } ?: return@withLock
        applyOps(stopwatchId, LapEdits.undoOps(LapEdits.decode(c.ops)))
        dao.upsertCorrection(c.copy(undone = true))
    }

    /** Redoes the earliest undone correction. */
    suspend fun redo(stopwatchId: String) = mutex.withLock {
        val c = dao.corrections(stopwatchId).firstOrNull { it.undone } ?: return@withLock
        applyOps(stopwatchId, LapEdits.decode(c.ops))
        dao.upsertCorrection(c.copy(undone = false))
    }

    private suspend fun correct(
        stopwatchId: String, lapId: String, kind: CorrectionKind, amount: Long, label: String,
        edit: (List<LapSnap>, StopwatchEntity, Long) -> Result<LapEdit>,
    ): EditError? = mutex.withLock {
        val s = dao.get(stopwatchId) ?: return EditError.NO_SUCH_LAP
        val laps = dao.laps(stopwatchId)
        val lapNumber = laps.sortedBy { it.elapsedMillis }.indexOfFirst { it.id == lapId } + 1
        val result = edit(laps.map { it.snap() }, s, now())
        val lapEdit = result.getOrElse { return (it as? EditException)?.error ?: EditError.OUT_OF_RANGE }
        applyOps(stopwatchId, lapEdit.ops)
        // A new correction ends any redo branch.
        dao.dropUndone(stopwatchId)
        val seq = (dao.corrections(stopwatchId).maxOfOrNull { it.seq } ?: 0) + 1
        dao.upsertCorrection(
            LapCorrectionEntity(
                UUID.randomUUID().toString(), stopwatchId, seq, kind.name, lapNumber, amount, label.trim(),
                LapEdits.encode(lapEdit.ops), encodeTags(lapEdit.affected), undone = false, createdAtMillis = now(),
            ),
        )
        null
    }

    /** Writes the result of [ops] and renumbers laps by boundary. */
    private suspend fun applyOps(stopwatchId: String, ops: List<LapOp>) {
        val before = dao.laps(stopwatchId)
        val after = LapEdits.apply(before.map { it.snap() }, ops)
        val afterIds = after.map { it.id }.toSet()
        before.filter { it.id !in afterIds }.forEach { dao.deleteLap(it.id) }
        after.forEachIndexed { i, snap ->
            dao.upsertLap(LapEntity(snap.id, stopwatchId, i + 1, snap.elapsedMillis, snap.note, encodeTags(snap.tags)))
        }
        dao.get(stopwatchId)?.let { publish(it, after.size) }
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

    private fun LapEntity.snap() = LapSnap(id, elapsedMillis, note, decodeTags(tags))
}

/** Per-tag numbers above the chart (spec §07): over the given laps' times. */
data class LapStats(val count: Int, val averageMillis: Long, val bestMillis: Long, val worstMillis: Long)

fun lapStats(laps: List<LapView>): LapStats? {
    if (laps.isEmpty()) return null
    val times = laps.map { it.deltaMillis }
    return LapStats(times.size, times.average().toLong(), times.min(), times.max())
}
