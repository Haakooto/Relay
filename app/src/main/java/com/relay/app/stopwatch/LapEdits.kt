package com.relay.app.stopwatch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** A lap as the correction logic sees it: an end boundary (total time) plus its details. */
@Serializable
data class LapSnap(val id: String, val elapsedMillis: Long, val note: String = "", val tags: List<String> = emptyList())

/** One reversible step on lap boundaries. A correction is a short list of these. */
@Serializable
sealed interface LapOp {
    @Serializable @SerialName("insert")
    data class Insert(val lap: LapSnap) : LapOp

    @Serializable @SerialName("delete")
    data class Delete(val lap: LapSnap) : LapOp

    @Serializable @SerialName("move")
    data class Move(val id: String, val from: Long, val to: Long) : LapOp

    @Serializable @SerialName("details")
    data class SetDetails(val id: String, val fromNote: String, val fromTags: List<String>, val toNote: String, val toTags: List<String>) : LapOp

    fun inverse(): LapOp = when (this) {
        is Insert -> Delete(lap)
        is Delete -> Insert(lap)
        is Move -> Move(id, to, from)
        is SetDetails -> SetDetails(id, toNote, toTags, fromNote, fromTags)
    }
}

enum class CorrectionKind { REMOVE, INSERT, ADJUST }

/** Why a correction was refused. */
enum class EditError { NO_SUCH_LAP, ZERO_AMOUNT, OUT_OF_RANGE }

/** The ops for one correction, plus the laps whose time it changed (they get the star). */
data class LapEdit(val ops: List<LapOp>, val affected: List<String>)

/**
 * Lap corrections (spec §07): move, add or remove lap boundaries without changing the total
 * time. Pure, unit-tested. Laps are ordered by end boundary; lap 1 starts at t0.
 */
object LapEdits {
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "op" }

    fun encode(ops: List<LapOp>): String = json.encodeToString(ops)
    fun decode(text: String): List<LapOp> = runCatching { json.decodeFromString<List<LapOp>>(text) }.getOrDefault(emptyList())

    /** Applies [ops] in order; laps come back sorted by boundary. */
    fun apply(laps: List<LapSnap>, ops: List<LapOp>): List<LapSnap> {
        val out = laps.associateBy { it.id }.toMutableMap()
        for (op in ops) when (op) {
            is LapOp.Insert -> out[op.lap.id] = op.lap
            is LapOp.Delete -> out.remove(op.lap.id)
            is LapOp.Move -> out[op.id]?.let { out[op.id] = it.copy(elapsedMillis = op.to) }
            is LapOp.SetDetails -> out[op.id]?.let { out[op.id] = it.copy(note = op.toNote, tags = op.toTags) }
        }
        return out.values.sortedBy { it.elapsedMillis }
    }

    fun undoOps(ops: List<LapOp>): List<LapOp> = ops.reversed().map { it.inverse() }

    /**
     * Removes a lap by merging it into the previous one (the merged lap keeps the previous lap's
     * note and tags). Lap 1 has no previous lap, so it merges into lap 2 instead.
     */
    fun remove(laps: List<LapSnap>, lapId: String): Result<LapEdit> {
        val sorted = laps.sortedBy { it.elapsedMillis }
        val i = sorted.indexOfFirst { it.id == lapId }
        if (i < 0) return fail(EditError.NO_SUCH_LAP)
        val lap = sorted[i]
        if (i == 0) return ok(LapEdit(listOf(LapOp.Delete(lap)), listOfNotNull(sorted.getOrNull(1)?.id)))
        val prev = sorted[i - 1]
        return ok(LapEdit(listOf(LapOp.Delete(lap), LapOp.Move(prev.id, prev.elapsedMillis, lap.elapsedMillis)), listOf(prev.id)))
    }

    /**
     * Splits a lap [intoMillis] after its start. The first part keeps the lap's note and tags; the
     * new second part starts blank. Lap 2 of 20 s split at 5 s becomes 5 s, then a new 15 s lap 3.
     */
    fun insert(laps: List<LapSnap>, t0Millis: Long, lapId: String, intoMillis: Long, newId: String = UUID.randomUUID().toString()): Result<LapEdit> {
        val sorted = laps.sortedBy { it.elapsedMillis }
        val i = sorted.indexOfFirst { it.id == lapId }
        if (i < 0) return fail(EditError.NO_SUCH_LAP)
        if (intoMillis == 0L) return fail(EditError.ZERO_AMOUNT)
        val lap = sorted[i]
        val start = if (i == 0) t0Millis else sorted[i - 1].elapsedMillis
        if (intoMillis < 0 || start + intoMillis >= lap.elapsedMillis) return fail(EditError.OUT_OF_RANGE)
        val first = LapSnap(newId, start + intoMillis, lap.note, lap.tags)
        return ok(
            LapEdit(
                listOf(LapOp.Insert(first), LapOp.SetDetails(lap.id, lap.note, lap.tags, "", emptyList())),
                listOf(newId, lap.id),
            ),
        )
    }

    /**
     * Moves a lap's end boundary by [deltaMillis], taking time from or giving it to the next lap
     * (or the still-running time after the last lap). No lap may end up shorter than zero.
     */
    fun adjust(laps: List<LapSnap>, t0Millis: Long, lapId: String, deltaMillis: Long, currentElapsedMillis: Long): Result<LapEdit> {
        val sorted = laps.sortedBy { it.elapsedMillis }
        val i = sorted.indexOfFirst { it.id == lapId }
        if (i < 0) return fail(EditError.NO_SUCH_LAP)
        if (deltaMillis == 0L) return fail(EditError.ZERO_AMOUNT)
        val lap = sorted[i]
        val lower = if (i == 0) t0Millis else sorted[i - 1].elapsedMillis
        val upper = sorted.getOrNull(i + 1)?.elapsedMillis ?: currentElapsedMillis
        val target = lap.elapsedMillis + deltaMillis
        if (target < lower || target > upper) return fail(EditError.OUT_OF_RANGE)
        return ok(LapEdit(listOf(LapOp.Move(lap.id, lap.elapsedMillis, target)), listOfNotNull(lap.id, sorted.getOrNull(i + 1)?.id)))
    }

    /**
     * A free-text amount with the relay language's units only: "5" (seconds), "5s", "5m", "1h",
     * "1m30s", "1:30", "-5s", "+2m", "1.5s". Null if it can't be read.
     */
    fun parseAmount(text: String): Long? {
        val t = text.trim().lowercase().replace(",", ".")
        if (t.isEmpty()) return null
        val sign = when (t[0]) { '-', '−' -> -1L; else -> 1L }
        val body = t.trimStart('+', '-', '−').trim()
        Regex("""^(\d+):(\d{1,2})(\.\d+)?$""").matchEntire(body)?.let { m ->
            val minutes = m.groupValues[1].toLong()
            val seconds = m.groupValues[2].toLong()
            if (seconds >= 60) return null
            val fraction = m.groupValues[3].takeIf { it.isNotEmpty() }?.toDouble() ?: 0.0
            return sign * ((minutes * 60 + seconds) * 1000 + Math.round(fraction * 1000))
        }
        val part = Regex("""(\d+(?:\.\d+)?)\s*([hms])?""")
        var rest = body
        var total = 0.0
        var any = false
        while (rest.isNotEmpty()) {
            val m = part.find(rest) ?: return null
            if (m.range.first != 0) return null
            val value = m.groupValues[1].toDouble()
            total += value * when (m.groupValues[2]) {
                "h" -> 3_600_000.0
                "m" -> 60_000.0
                else -> 1_000.0
            }
            any = true
            rest = rest.substring(m.range.last + 1).trimStart()
        }
        return if (any) sign * Math.round(total) else null
    }

    private fun ok(edit: LapEdit) = Result.success(edit)
    private fun fail(error: EditError) = Result.failure<LapEdit>(EditException(error))
}

class EditException(val error: EditError) : Exception(error.name)
