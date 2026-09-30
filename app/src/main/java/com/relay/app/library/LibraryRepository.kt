package com.relay.app.library

import com.relay.app.data.library.LibraryDao
import com.relay.app.data.library.LibraryHeaderDao
import com.relay.app.data.library.LibraryHeaderEntity
import com.relay.app.data.library.RelayDefEntity
import com.relay.app.data.library.SoundSpec
import com.relay.app.data.library.TimerDefEntity
import com.relay.app.data.run.RunKind
import com.relay.app.data.settings.AppSettings
import com.relay.app.run.Library
import com.relay.app.run.PlanBuilder
import com.relay.app.run.RelaySpec
import com.relay.app.run.RunRepository
import com.relay.app.run.RunStep
import com.relay.app.run.RunView
import com.relay.app.run.StepOverrides
import com.relay.app.run.TimerSpec
import com.relay.chainlang.ChainLang
import com.relay.chainlang.ChainLangException
import com.relay.chainlang.CycleChecker
import com.relay.chainlang.CycleException
import com.relay.chainlang.NowClock
import com.relay.chainlang.SafetyLimits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

enum class BlockKind { TIMER, RELAY }

/** A row of the Timers library (spec §05): summary only. [totalSeconds] null = broken. */
data class LibraryItem(
    val id: String,
    val label: String,
    val kind: BlockKind,
    val totalSeconds: Long?,
    val headerId: String?,
    val sortOrder: Long,
)

data class LibraryState(val items: List<LibraryItem>, val headers: List<LibraryHeaderEntity>)

/** Why a save was refused. The UI turns these into text. */
sealed interface SaveError {
    data object LabelRequired : SaveError
    data object LabelNotReferenceable : SaveError
    data object LabelTaken : SaveError
    data class Invalid(val message: String) : SaveError
    data class Cycle(val path: List<String>) : SaveError
}

sealed interface SaveResult {
    data class Saved(val id: String) : SaveResult
    data class Refused(val error: SaveError) : SaveResult
}

/**
 * The command layer for saved Timers and Relays (spec §05/§06), and the one place runs get
 * started from definitions. Label rules: a saved block's label is unique across timers and
 * relays and must be a script identifier, since relays refer to it by that label.
 */
class LibraryRepository(
    private val dao: LibraryDao,
    private val headers: LibraryHeaderDao,
    private val runs: RunRepository,
    private val settings: suspend () -> AppSettings,
) {
    private val mutex = Mutex()

    fun observe(): Flow<LibraryState> = combine(dao.observeTimers(), dao.observeRelays(), headers.observeAll()) { timers, relays, hs ->
        val library = snapshotOf(timers, relays)
        val builder = PlanBuilder(library, NowClock.now())
        val items = timers.map { t ->
            LibraryItem(t.id, t.label, BlockKind.TIMER, runCatching { builder.timerSeconds(t.spec()) }.getOrNull(), t.headerId, t.sortOrder)
        } + relays.map { r ->
            LibraryItem(r.id, r.label, BlockKind.RELAY, runCatching { builder.forRelay(r.spec()).sumOf { it.seconds } }.getOrNull(), r.headerId, r.sortOrder)
        }
        LibraryState(items.sortedBy { it.sortOrder }, hs)
    }

    suspend fun timer(id: String): TimerDefEntity? = dao.timer(id)
    suspend fun relay(id: String): RelayDefEntity? = dao.relay(id)

    /** Everything saved, as the plan builder sees it. */
    suspend fun snapshot(): Library = snapshotOf(dao.timers(), dao.relays())

    // --- saving ---------------------------------------------------------------------------

    suspend fun saveTimer(timer: TimerDefEntity): SaveResult = mutex.withLock {
        validateLabel(timer.id, timer.label)?.let { return SaveResult.Refused(it) }
        try {
            PlanBuilder(snapshot(), NowClock.now()).timerSeconds(timer.spec())
        } catch (e: ChainLangException) {
            return SaveResult.Refused(SaveError.Invalid(e.message.orEmpty()))
        }
        val old = dao.timer(timer.id)
        dao.upsertTimer(timer)
        if (old != null && old.label != timer.label) renameReferences(old.label, timer.label)
        SaveResult.Saved(timer.id)
    }

    suspend fun saveRelay(relay: RelayDefEntity): SaveResult = mutex.withLock {
        validateLabel(relay.id, relay.label)?.let { return SaveResult.Refused(it) }
        val old = dao.relay(relay.id)
        // The graph after this save, including other relays' references renamed along with it.
        val others = dao.relays().filter { it.id != relay.id }.map {
            if (old != null && old.label != relay.label) it.copy(script = ScriptRefs.rename(it.script, old.label, relay.label)) else it
        }
        val all = others + relay
        try {
            CycleChecker.checkAcyclic(all.map { it.label }) { label ->
                all.firstOrNull { it.label == label }?.let { r -> runCatching { ChainLang.referencedLabels(r.script) }.getOrDefault(emptySet()) }
                    ?: emptySet()
            }
            PlanBuilder(snapshotOf(dao.timers(), all), NowClock.now(), limitsFor(settings())).forRelay(relay.spec())
        } catch (e: CycleException) {
            return SaveResult.Refused(SaveError.Cycle(e.cyclePath))
        } catch (e: ChainLangException) {
            return SaveResult.Refused(SaveError.Invalid(e.message.orEmpty()))
        }
        dao.upsertRelay(relay)
        if (old != null && old.label != relay.label) renameReferences(old.label, relay.label)
        SaveResult.Saved(relay.id)
    }

    suspend fun deleteTimer(id: String) = mutex.withLock { dao.deleteTimer(id) }
    suspend fun deleteRelay(id: String) = mutex.withLock { dao.deleteRelay(id) }

    /** Relays whose scripts mention [label]: they break if it's deleted. */
    suspend fun referencing(label: String): List<String> =
        dao.relays().filter { r -> label in runCatching { ChainLang.referencedLabels(r.script) }.getOrDefault(emptySet()) }.map { it.label }

    /**
     * Names the relay [selfLabel] may reference: every saved label except itself and relays that
     * already (directly or not) reference it, which would close a cycle.
     */
    suspend fun referenceCandidates(selfLabel: String): List<String> {
        val relays = dao.relays()
        val edges = relays.associate { r -> r.label to runCatching { ChainLang.referencedLabels(r.script) }.getOrDefault(emptySet()) }
        fun reaches(from: String, target: String, seen: MutableSet<String> = mutableSetOf()): Boolean {
            if (!seen.add(from)) return false
            return edges[from].orEmpty().any { it == target || reaches(it, target, seen) }
        }
        val labels = dao.timers().map { it.label } + relays.map { it.label }
        return labels.filter { it != selfLabel && !(selfLabel.isNotBlank() && reaches(it, selfLabel)) }.sorted()
    }

    // --- starting runs --------------------------------------------------------------------

    suspend fun startTimer(id: String): RunView? {
        val t = dao.timer(id) ?: return null
        return startTimerSpec(t.spec(), sourceId = t.id)
    }

    suspend fun startRelay(id: String): RunView? {
        val r = dao.relay(id) ?: return null
        return startRelaySpec(r.spec(), sourceId = r.id)
    }

    /** Unsaved (or draft) timer: the run keeps the label, nothing is saved. */
    suspend fun startTimerSpec(spec: TimerSpec, sourceId: String? = null): RunView {
        val plan = builder().forTimer(spec)
        return runs.start(RunKind.TIMER, sourceId, spec.label, plan)
    }

    suspend fun startRelaySpec(spec: RelaySpec, sourceId: String? = null): RunView {
        val plan = builder().forRelay(spec)
        return runs.start(RunKind.RELAY, sourceId, spec.label, plan)
    }

    /** The quick-start chips: an anonymous timer of [seconds]. */
    suspend fun quickStart(seconds: Long): RunView {
        val s = settings()
        val plan = listOf(
            RunStep(null, seconds, s.defaultTimerSoundUri, true, com.relay.app.data.library.TransitionMode.SEAMLESS, com.relay.app.data.library.TransitionTone.SILENT),
        )
        return runs.start(RunKind.TIMER, null, "", plan)
    }

    suspend fun builder(): PlanBuilder {
        val s = settings()
        return PlanBuilder(snapshot(), NowClock.now(), limitsFor(s), SoundSpec(s.defaultTimerSoundUri, true))
    }

    // --- headers and order ----------------------------------------------------------------

    suspend fun addHeader(title: String) = headers.upsert(LibraryHeaderEntity(UUID.randomUUID().toString(), title.trim(), System.currentTimeMillis()))
    suspend fun renameHeader(header: LibraryHeaderEntity, title: String) = headers.upsert(header.copy(title = title.trim()))
    suspend fun moveHeader(header: LibraryHeaderEntity, sortOrder: Long) = headers.upsert(header.copy(sortOrder = sortOrder))

    suspend fun deleteHeader(id: String) = mutex.withLock {
        dao.ungroupTimers(id)
        dao.ungroupRelays(id)
        headers.delete(id)
    }

    suspend fun moveBlock(item: LibraryItem, headerId: String?, sortOrder: Long) = when (item.kind) {
        BlockKind.TIMER -> dao.moveTimer(item.id, headerId, sortOrder)
        BlockKind.RELAY -> dao.moveRelay(item.id, headerId, sortOrder)
    }

    // --- internals ------------------------------------------------------------------------

    private suspend fun validateLabel(id: String, label: String): SaveError? {
        if (label.isBlank()) return SaveError.LabelRequired
        if (!ChainLang.isReferenceableLabel(label)) return SaveError.LabelNotReferenceable
        val taken = dao.timers().any { it.id != id && it.label == label } || dao.relays().any { it.id != id && it.label == label }
        return if (taken) SaveError.LabelTaken else null
    }

    private suspend fun renameReferences(old: String, new: String) {
        for (r in dao.relays()) {
            val renamed = ScriptRefs.rename(r.script, old, new)
            if (renamed != r.script) dao.upsertRelay(r.copy(script = renamed))
        }
    }

    companion object {
        fun limitsFor(s: AppSettings) = SafetyLimits(maxPlanSize = s.maxPlanSize)

        fun snapshotOf(timers: List<TimerDefEntity>, relays: List<RelayDefEntity>): Library {
            val t = timers.associateBy { it.label }
            val r = relays.associateBy { it.label }
            return object : Library {
                override fun timer(label: String) = t[label]?.spec()
                override fun relay(label: String) = r[label]?.spec()
            }
        }
    }
}

fun TimerDefEntity.spec() = TimerSpec(label, durationExpr, sound)

fun RelayDefEntity.spec() = RelaySpec(label, script, defaultSound, transitionMode, transitionTone, StepOverrides.decode(stepOverrides))
