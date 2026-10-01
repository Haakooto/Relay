package com.relay.app.clock

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ClockKind { DIGITAL, ANALOGUE }

/** Bundled faces (SIL OFL, licences in assets/licenses) plus the system ones. */
enum class DigitalFont { SYSTEM, SERIF, MONO, SEVEN_SEGMENT, DISPLAY }

enum class HourFormat { FOLLOW_SETTING, H12, H24 }

enum class DateStyle { OFF, SHORT, LONG }

enum class HandStyle { THICK, THIN, DOT }

/** MINUTES = 60 small marks with bolder hours; HOURS = 12; QUARTERS = 12, 3, 6 and 9 only. */
enum class TickStyle { NONE, MINUTES, HOURS, QUARTERS }

enum class FaceStyle { NONE, RING, FILLED, NUMERALS }

/** Per-element colours, ARGB. Null = follow the theme. */
@Serializable
data class ClockColors(
    val digits: Long? = null,
    val secondary: Long? = null,
    val hourHand: Long? = null,
    val minuteHand: Long? = null,
    val secondHand: Long? = null,
    val ticks: Long? = null,
    val face: Long? = null,
)

/**
 * How a clock looks (spec §08), shared by the Clock pane's main clock and every widget, so one
 * renderer draws both.
 */
@Serializable
data class ClockStyle(
    val kind: ClockKind = ClockKind.DIGITAL,
    val font: DigitalFont = DigitalFont.DISPLAY,
    val format: HourFormat = HourFormat.FOLLOW_SETTING,
    val showSeconds: Boolean = true,
    val date: DateStyle = DateStyle.SHORT,
    val hands: HandStyle = HandStyle.THIN,
    val ticks: TickStyle = TickStyle.MINUTES,
    val face: FaceStyle = FaceStyle.RING,
    val secondHand: Boolean = true,
    val colors: ClockColors = ClockColors(),
) {
    fun is24h(setting: Boolean) = when (format) {
        HourFormat.FOLLOW_SETTING -> setting
        HourFormat.H12 -> false
        HourFormat.H24 -> true
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        fun decode(text: String?): ClockStyle =
            text?.let { runCatching { json.decodeFromString<ClockStyle>(it) }.getOrNull() } ?: ClockStyle()
        fun encode(style: ClockStyle): String = json.encodeToString(style)
    }
}
