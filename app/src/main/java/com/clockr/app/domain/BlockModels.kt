package com.clockr.app.domain

/** Spec §03 RunnableBlock's two concrete shapes. */
enum class BlockKind { TIMER, CHAIN }

/** Spec §6.2 — governs advancing between a Chain's own steps. */
enum class TransitionMode { REQUIRE_INTERACTION, SEAMLESS }

/** One row of the Timers library list (spec §6.1) — summary only, no content/sound detail. */
data class LibraryItem(
    val id: String,
    val label: String,
    val kind: BlockKind,
    val totalSeconds: Long,
)
