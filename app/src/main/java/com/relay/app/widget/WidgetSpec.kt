package com.relay.app.widget

import com.relay.app.clock.ClockStyle
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class WidgetBackground { FOLLOW_THEME, TRANSPARENT, SOLID, GRADIENT }

enum class WidgetCorners { SHARP, ROUNDED, PILL }

/**
 * Everything one widget instance looks like (spec §09): its clock style and location, plus the
 * widget-only background, shape and border. Colours are ARGB; null = a theme default.
 */
@Serializable
data class WidgetSpec(
    val style: ClockStyle = ClockStyle(showSeconds = false),
    /** Null = the phone's current time zone. */
    val zoneId: String? = null,
    /** Shown with the date when set. */
    val city: String? = null,
    val background: WidgetBackground = WidgetBackground.FOLLOW_THEME,
    val backgroundColor: Long? = null,
    val backgroundColor2: Long? = null,
    val corners: WidgetCorners = WidgetCorners.ROUNDED,
    val borderWidthDp: Int = 0,
    val borderColor: Long? = null,
) {
    val frame: WidgetFrame get() = WidgetFrame(background, backgroundColor, backgroundColor2, corners, borderWidthDp, borderColor)

    fun withFrame(f: WidgetFrame) = copy(
        background = f.background, backgroundColor = f.backgroundColor, backgroundColor2 = f.backgroundColor2,
        corners = f.corners, borderWidthDp = f.borderWidthDp, borderColor = f.borderColor,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        fun decode(text: String?): WidgetSpec = text?.let { runCatching { json.decodeFromString<WidgetSpec>(it) }.getOrNull() } ?: WidgetSpec()
        fun encode(spec: WidgetSpec): String = json.encodeToString(spec)
    }
}
