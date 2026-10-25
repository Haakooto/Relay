package com.relay.app.widget.calendar

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.relay.app.MainActivity
import com.relay.app.appContainer
import com.relay.app.data.settings.AppSettings
import com.relay.app.event.CalendarEntry
import com.relay.app.event.CalendarOccurrences
import com.relay.app.event.EntryKind
import com.relay.app.widget.WidgetBackground
import com.relay.app.widget.WidgetFrame
import com.relay.app.widget.WidgetTheme
import java.time.LocalDate

/** The colours a calendar widget's text uses: from the theme, or picked to stand out on its background. */
data class WidgetColors(val text: Int, val muted: Int, val accent: Int, val onAccent: Int) {
    companion object {
        private const val LIGHT_BACKGROUND = 0.5
        private const val DARK_TEXT = 0xDE000000.toInt()
        private const val DARK_MUTED = 0x99000000.toInt()
        private const val LIGHT_MUTED = 0xB3FFFFFF.toInt()

        fun of(frame: WidgetFrame, theme: WidgetTheme): WidgetColors {
            val background = when (frame.background) {
                WidgetBackground.FOLLOW_THEME -> return WidgetColors(theme.onSurface, theme.muted, theme.accent, theme.surface)
                // Over an unknown wallpaper, light text is what launchers use.
                WidgetBackground.TRANSPARENT -> Color.BLACK
                WidgetBackground.SOLID -> frame.backgroundColor?.toInt() ?: theme.surface
                WidgetBackground.GRADIENT ->
                    blend(frame.backgroundColor?.toInt() ?: theme.accent, frame.backgroundColor2?.toInt() ?: theme.accent)
            }
            return if (Color.luminance(background) > LIGHT_BACKGROUND) {
                WidgetColors(DARK_TEXT, DARK_MUTED, DARK_TEXT, Color.WHITE)
            } else {
                WidgetColors(Color.WHITE, LIGHT_MUTED, Color.WHITE, Color.BLACK)
            }
        }

        private fun blend(a: Int, b: Int): Int =
            Color.rgb((Color.red(a) + Color.red(b)) / 2, (Color.green(a) + Color.green(b)) / 2, (Color.blue(a) + Color.blue(b)) / 2)
    }
}

/** What the calendar widgets share: state keys, refreshing, and the intents that open the app. */
object CalendarWidgets {
    val KEY_SPEC = stringPreferencesKey("calendar_spec")

    /** Changed to make a widget recompose: midnight, a setting, a data change. */
    val KEY_TICK = longPreferencesKey("calendar_tick")

    private val kinds: List<() -> GlanceAppWidget> = listOf(::AgendaWidget, ::MonthWidget)

    /** Re-reads every placed calendar widget's settings and redraws it. Cheap when none are placed. */
    suspend fun refreshAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val clocks = context.appContainer.clocks
        val now = System.currentTimeMillis()
        for (make in kinds) {
            val widget = make()
            for (id in manager.getGlanceIds(widget.javaClass)) {
                val spec = clocks.widget(manager.getAppWidgetId(id))?.spec
                updateAppWidgetState(context, id) { prefs ->
                    if (spec != null) prefs[KEY_SPEC] = spec
                    prefs[KEY_TICK] = now
                }
                widget.update(context, id)
            }
        }
    }

    /** Whether any calendar widget is placed, so the midnight tick can stop when none is. */
    suspend fun anyPlaced(context: Context): Boolean {
        val manager = GlanceAppWidgetManager(context)
        return kinds.any { manager.getGlanceIds(it().javaClass).isNotEmpty() }
    }

    // Each intent gets its own data URI: PendingIntents that differ only in extras would merge.
    fun openItem(context: Context, id: String) = launch(context, "item/$id").putExtra(MainActivity.EXTRA_OPEN_ITEM, id)

    fun openAlarm(context: Context, id: String) = launch(context, "alarm/$id").putExtra(MainActivity.EXTRA_OPEN_ALARM, id)

    fun newEvent(context: Context, day: LocalDate) =
        launch(context, "new/${day.toEpochDay()}").putExtra(MainActivity.EXTRA_NEW_EVENT_DAY, day.toEpochDay())

    fun showDay(context: Context, day: LocalDate) =
        launch(context, "day/${day.toEpochDay()}").putExtra(MainActivity.EXTRA_CALENDAR_DAY, day.toEpochDay())

    /** The intent for an entry's row: its editor, or the alarm editor. */
    fun open(context: Context, entry: CalendarEntry): Intent =
        if (entry.kind == EntryKind.ALARM) openAlarm(context, entry.id) else openItem(context, entry.id)

    private fun launch(context: Context, path: String) = Intent(context, MainActivity::class.java)
        .setData(Uri.parse("relay://widget/$path"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

/** The app settings, kept current while the widget is showing; null until first read. */
@Composable
fun widgetSettings(context: Context): AppSettings? {
    // Remembered: a new flow each recomposition would restart the collection every time.
    val flow = remember { context.appContainer.settings.settings }
    val settings by flow.collectAsState(initial = null)
    return settings
}

/** The entries from [from] to [to] in [spec]'s calendars, kept current while the widget shows; null until first read. */
@Composable
fun calendarEntries(context: Context, from: LocalDate, to: LocalDate, spec: CalendarWidgetSpec): Map<LocalDate, List<CalendarEntry>>? {
    val container = context.appContainer
    val eventsFlow = remember { container.events.observeAll() }
    val schedulesFlow = remember { container.schedules.observeWithExceptions() }
    val calendarsFlow = remember { container.calendars.observeAll() }
    val events by eventsFlow.collectAsState(initial = null)
    val schedules by schedulesFlow.collectAsState(initial = null)
    val calendars by calendarsFlow.collectAsState(initial = null)
    val e = events ?: return null
    val (list, exceptions) = schedules ?: return null
    val c = calendars ?: return null
    return CalendarOccurrences.between(e, list, exceptions, spec.selection(c), from, to, LocalDate.now())
}
