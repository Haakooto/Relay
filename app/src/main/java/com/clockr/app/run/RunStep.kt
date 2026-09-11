package com.clockr.app.run

import com.clockr.app.data.TransitionBeepTone
import com.clockr.app.domain.TransitionMode

/**
 * One flattened, ready-to-execute step of a run — whether the run started as a standalone Timer
 * (a plan of exactly one step) or a Chain (built by recursively expanding every reference,
 * including nested chain references, via [RunPlanBuilder]).
 */
data class RunStep(
    val label: String?,
    val seconds: Long,
    val soundProfileId: String?,
    /** Governs advancing to the *next* step in the plan — meaningless for the last step. */
    val transitionMode: TransitionMode,
    /** Which Chain's own setting governs a SEAMLESS boundary leaving this step — same
     *  per-chain-not-global reasoning as [transitionMode] itself. Meaningless when
     *  [transitionMode] isn't [TransitionMode.SEAMLESS]. */
    val transitionBeepTone: TransitionBeepTone,
)
