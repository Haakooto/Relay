package com.relay.app.ui.alarms

import com.relay.app.AppContainer
import com.relay.app.data.library.RelayDefEntity
import com.relay.app.data.library.SoundSpec
import com.relay.app.data.library.TimerDefEntity
import com.relay.app.data.library.TransitionTone
import com.relay.app.data.schedule.TargetKind
import com.relay.app.library.BlockKind
import com.relay.app.library.SaveError
import com.relay.app.library.SaveResult
import com.relay.app.library.spec
import com.relay.app.run.RelaySpec
import com.relay.app.run.TransitionBeep
import com.relay.app.schedule.TargetSpec
import com.relay.chainlang.ChainLang
import com.relay.chainlang.ChainLangException
import com.relay.chainlang.NowClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** A saved timer or relay to pick, with its length and step count (null = currently broken). */
data class SavedBlock(val id: String, val label: String, val totalSeconds: Long?, val steps: Int?)

/** What a schedule fires, as the editor holds it (spec §04). */
data class TargetDraft(
    val target: TargetKind = TargetKind.RING,
    /** A saved timer/relay; null = the inline one in [spec]. */
    val targetDefId: String? = null,
    val spec: TargetSpec = TargetSpec(),
    val savedTimers: List<SavedBlock> = emptyList(),
    val savedRelays: List<SavedBlock> = emptyList(),
    /** Inline timer: its length, or null when the expression is broken. */
    val inlineTimerSeconds: Long? = 300,
    /** Inline relay: total, or null with [inlineError]. */
    val inlineRelaySeconds: Long? = null,
    val inlineError: String? = null,
    val labelCandidates: List<String> = emptyList(),
    val promoteError: SaveError? = null,
)

/**
 * The "Fires" part of an editor: picking a target, building an inline timer or relay, and saving
 * it to the library. Shared by every editor that makes a schedule, so they all offer the same
 * targets with the same checks. Lives in its owner's [scope].
 */
class TargetEditor(private val container: AppContainer, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(TargetDraft())
    val state: StateFlow<TargetDraft> = _state.asStateFlow()

    /** Starts from a stored target and loads the library to pick from. */
    suspend fun load(target: TargetKind, targetDefId: String?, spec: TargetSpec) {
        _state.update { it.copy(target = target, targetDefId = targetDefId, spec = spec) }
        loadLibrary()
        revalidate().join()
    }

    /** The parts a save stores, for the owner's unsaved-changes check. */
    fun editable(): List<Any?> = _state.value.let { listOf(it.target, it.targetDefId, it.spec) }

    /** Whether Save can work: a ring or a notification always can; a target needs something valid to start. */
    fun canSave(d: TargetDraft = _state.value): Boolean = when (d.target) {
        TargetKind.RING, TargetKind.STOPWATCH -> true
        TargetKind.TIMER -> d.targetDefId != null || d.inlineTimerSeconds != null
        TargetKind.RELAY -> d.targetDefId != null || (d.inlineRelaySeconds != null && d.inlineError == null)
    }

    /** The saved definition to store: only a timer or relay has one. */
    fun storedDefId(d: TargetDraft = _state.value): String? =
        if (d.target == TargetKind.TIMER || d.target == TargetKind.RELAY) d.targetDefId else null

    fun setTarget(kind: TargetKind) {
        _state.update {
            // Keep a saved pick only if it's the right kind.
            val keep = it.targetDefId != null && ((kind == TargetKind.TIMER && it.savedTimers.any { t -> t.id == it.targetDefId }) ||
                (kind == TargetKind.RELAY && it.savedRelays.any { r -> r.id == it.targetDefId }))
            it.copy(target = kind, targetDefId = if (keep) it.targetDefId else null, promoteError = null)
        }
        revalidate()
    }

    fun setStartTone(tone: TransitionTone) {
        setSpec { it.copy(startTone = tone) }
        scope.launch(Dispatchers.Default) { TransitionBeep.play(tone) }
    }

    fun setTargetDef(id: String?) { _state.update { it.copy(targetDefId = id, promoteError = null) } }

    fun setSpec(transform: (TargetSpec) -> TargetSpec) {
        _state.update { it.copy(spec = transform(it.spec), promoteError = null) }
        revalidate()
    }

    private fun revalidate(): Job = scope.launch {
        val s = _state.value
        val now = NowClock.now()
        val timerSeconds = runCatching { ChainLang.run("(${s.spec.durationExpr})", now).singleOrNull()?.durationSeconds }.getOrNull()
        val (relaySeconds, relayError) = try {
            val plan = container.library.builder().forRelay(RelaySpec(s.spec.label.ifBlank { "(this relay)" }, s.spec.script, SoundSpec(), s.spec.mode, s.spec.tone))
            plan.sumOf { it.seconds } to null
        } catch (e: ChainLangException) {
            null to e.message
        }
        _state.update { it.copy(inlineTimerSeconds = timerSeconds, inlineRelaySeconds = relaySeconds, inlineError = relayError) }
    }

    /** "Save to library": turns the inline timer or relay into a saved one the schedule then uses. */
    fun promote() = scope.launch {
        val s = _state.value
        val id = UUID.randomUUID().toString()
        val result = when (s.target) {
            TargetKind.TIMER -> container.library.saveTimer(
                TimerDefEntity(id, s.spec.label.trim(), s.spec.durationExpr.trim(), SoundSpec(), null, System.currentTimeMillis()),
            )
            TargetKind.RELAY -> container.library.saveRelay(
                RelayDefEntity(id, s.spec.label.trim(), s.spec.script, SoundSpec(), s.spec.mode, s.spec.tone, "", null, System.currentTimeMillis()),
            )
            else -> return@launch
        }
        when (result) {
            is SaveResult.Saved -> {
                loadLibrary()
                _state.update { it.copy(targetDefId = result.id, promoteError = null) }
            }
            is SaveResult.Refused -> _state.update { it.copy(promoteError = result.error) }
        }
    }

    private suspend fun loadLibrary() {
        val items = container.library.observe().first().items
        val builder = container.library.builder()
        val relaySteps = items.filter { it.kind == BlockKind.RELAY }.associate { i ->
            i.id to runCatching { container.library.relay(i.id)?.let { r -> builder.forRelay(r.spec()).size } }.getOrNull()
        }
        _state.update {
            it.copy(
                savedTimers = items.filter { i -> i.kind == BlockKind.TIMER }.map { i -> SavedBlock(i.id, i.label, i.totalSeconds, 1) },
                savedRelays = items.filter { i -> i.kind == BlockKind.RELAY }.map { i -> SavedBlock(i.id, i.label, i.totalSeconds, relaySteps[i.id]) },
                labelCandidates = items.map { i -> i.label }.sorted(),
            )
        }
    }
}
