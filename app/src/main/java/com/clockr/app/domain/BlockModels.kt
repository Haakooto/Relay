package com.clockr.app.domain

/** Spec §03 RunnableBlock's two concrete shapes. CHAIN is shown to the user as "Relay" (v0.5
 *  rename, UI-only — see CLAUDE.md's naming note). */
enum class BlockKind { TIMER, CHAIN }

/** Spec §6.2 — governs advancing between a Chain's own steps. */
enum class TransitionMode { REQUIRE_INTERACTION, SEAMLESS }

/** One row of the Timers library list (spec §6.1) — summary only, no content/sound detail. */
data class LibraryItem(
    val id: String,
    val label: String,
    val kind: BlockKind,
    val totalSeconds: Long,
    /** Which [LibraryHeader] it's grouped under, if any — null means ungrouped. */
    val headerId: String? = null,
    val sortOrder: Long = 0L,
)

/** A user-named, user-orderable section header in the Timers library list. */
data class LibraryHeader(
    val id: String,
    val title: String,
    val sortOrder: Long,
)
