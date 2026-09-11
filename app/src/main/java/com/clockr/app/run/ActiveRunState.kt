package com.clockr.app.run

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class RunPhase { RUNNING, AWAITING_INTERACTION, AWAITING_COMPLETION }

data class ActiveRunSnapshot(
    val blockLabel: String,
    val stepLabel: String?,
    val stepIndex: Int,
    val stepCount: Int,
    val stepSeconds: Long,
    val phase: RunPhase,
    /** Wall-clock time the current step ends — lets the UI tick a live countdown itself instead
     *  of freezing at [stepSeconds] (only meaningful while [phase] is [RunPhase.RUNNING]). */
    val stepEndAtMillis: Long = 0L,
)

/**
 * In-process shared state between [TimerRunService] and the UI — a single-process app doesn't
 * need a bound service/AIDL/Messenger for this, just a StateFlow both sides can see. Scope v1:
 * exactly one active run at a time (unlike Stopwatches, which are explicitly multi-instance).
 */
object ActiveRunState {
    private val _current = MutableStateFlow<ActiveRunSnapshot?>(null)
    val current: StateFlow<ActiveRunSnapshot?> = _current

    fun update(snapshot: ActiveRunSnapshot?) {
        _current.value = snapshot
    }
}
