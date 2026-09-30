package com.relay.app.run

import com.relay.app.data.library.StepOverride
import com.relay.app.data.library.TransitionMode
import com.relay.app.data.library.TransitionTone
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One step of a fully expanded run plan. [transitionAfter]/[toneAfter] describe the boundary
 * *after* this step; on the last step they're unused (completion takes over).
 */
@Serializable
data class RunStep(
    val label: String?,
    val seconds: Long,
    /** Already resolved through overrides and defaults; null = the system alarm sound. */
    val soundUri: String?,
    val vibrate: Boolean,
    val transitionAfter: TransitionMode,
    val toneAfter: TransitionTone,
)

private val json = Json { ignoreUnknownKeys = true }

object RunPlans {
    fun encode(plan: List<RunStep>): String = json.encodeToString(plan)
    fun decode(text: String): List<RunStep> = json.decodeFromString(text)
}

object StepOverrides {
    fun encode(map: Map<Int, StepOverride>): String = json.encodeToString(map.filterValues { it.soundUri != null || it.transition != null })
    fun decode(text: String): Map<Int, StepOverride> =
        if (text.isBlank()) emptyMap() else runCatching { json.decodeFromString<Map<Int, StepOverride>>(text) }.getOrDefault(emptyMap())
}
