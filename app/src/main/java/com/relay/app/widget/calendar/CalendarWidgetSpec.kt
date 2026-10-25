package com.relay.app.widget.calendar

import com.relay.app.data.event.CalendarEntity
import com.relay.app.event.CalendarSelection
import com.relay.app.widget.WidgetBackground
import com.relay.app.widget.WidgetCorners
import com.relay.app.widget.WidgetFrame
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One calendar widget's settings (spec §10b, §09): which calendars it shows and its frame. Stored
 * as JSON in `widget_configs`, keyed by the widget, like the clock's. Fields only ever gain defaults.
 */
@Serializable
data class CalendarWidgetSpec(
    /** From before calendars (#34): alarms too. Only read for a widget set up before them. */
    val showAll: Boolean = false,
    /** The calendars it shows; null = set up before calendars, see [selection]. */
    val calendarIds: List<String>? = null,
    val background: WidgetBackground = WidgetBackground.FOLLOW_THEME,
    val backgroundColor: Long? = null,
    val backgroundColor2: Long? = null,
    val corners: WidgetCorners = WidgetCorners.ROUNDED,
    val borderWidthDp: Int = 0,
    val borderColor: Long? = null,
) {
    /** What it shows. A widget from before calendars shows all but the alarms one, unless it showed alarms. */
    fun selection(calendars: List<CalendarEntity>): CalendarSelection = CalendarSelection(
        calendars,
        calendarIds?.toSet() ?: calendars.filter { showAll || !it.defaultForAlarms }.map { it.id }.toSet(),
    )

    val frame: WidgetFrame get() = WidgetFrame(background, backgroundColor, backgroundColor2, corners, borderWidthDp, borderColor)

    fun withFrame(f: WidgetFrame) = copy(
        background = f.background, backgroundColor = f.backgroundColor, backgroundColor2 = f.backgroundColor2,
        corners = f.corners, borderWidthDp = f.borderWidthDp, borderColor = f.borderColor,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        fun decode(text: String?): CalendarWidgetSpec =
            text?.let { runCatching { json.decodeFromString<CalendarWidgetSpec>(it) }.getOrNull() } ?: CalendarWidgetSpec()

        fun encode(spec: CalendarWidgetSpec): String = json.encodeToString(spec)
    }
}
