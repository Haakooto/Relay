package com.relay.app.widget.calendar

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import com.relay.app.R
import com.relay.app.event.CalendarEntry
import com.relay.app.event.CalendarViews
import com.relay.app.event.EntryKind
import com.relay.app.event.GRID_DAYS
import com.relay.app.ui.calendar.CalendarText
import com.relay.app.ui.theme.Accent
import com.relay.app.ui.theme.Brass
import com.relay.app.ui.theme.Teal
import com.relay.app.util.AppLocale
import com.relay.app.util.RelativeText
import com.relay.app.widget.WidgetTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

/**
 * The month widget (spec §10b, #15), like Google Calendar's: a plain month where each day lists
 * its items as small labels, coloured by kind, with "+n" when they don't fit. The arrows turn the
 * month in place; a day or the month's name opens the Calendar pane there.
 */
class MonthWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Month() }
    }

    @Composable
    private fun Month() {
        val context = LocalContext.current
        val prefs = currentState<Preferences>()
        prefs[CalendarWidgets.KEY_TICK] // read so a refresh recomposes
        val spec = CalendarWidgetSpec.decode(prefs[CalendarWidgets.KEY_SPEC])
        val colors = WidgetColors.of(spec.frame, WidgetTheme.of(context))
        val today = LocalDate.now()
        val month = prefs[KEY_MONTH]?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: YearMonth.from(today)
        val gridStart = CalendarViews.gridStart(month, widgetSettings(context)?.firstDayOfWeek ?: DayOfWeek.MONDAY)
        val entries = calendarEntries(context, gridStart, gridStart.plusDays(GRID_DAYS - 1), spec.showAll).orEmpty()
        // How many labels a day cell has room for, from the widget's height.
        val cellHeight = (LocalSize.current.height - CHROME_HEIGHT) / WEEKS
        val labels = ((cellHeight - DAY_NUMBER_HEIGHT) / LABEL_HEIGHT).toInt().coerceIn(0, MAX_LABELS)
        val grid = MonthGridState(month, today, entries, labels)

        Box(GlanceModifier.fillMaxSize()) {
            FrameBackground(context, spec.frame)
            Column(GlanceModifier.fillMaxSize().padding(6.dp)) {
                MonthHeader(context, month, colors)
                Row(GlanceModifier.fillMaxWidth()) {
                    for (d in 0 until DAYS_PER_WEEK) {
                        WidgetText(
                            gridStart.plusDays(d.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, AppLocale.formatting),
                            colors.muted, 10.sp, GlanceModifier.defaultWeight(), align = TextAlign.Center,
                        )
                    }
                }
                for (week in 0 until WEEKS) {
                    Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                        for (d in 0 until DAYS_PER_WEEK) {
                            DayCell(context, grid, gridStart.plusDays(week * DAYS_PER_WEEK + d.toLong()), colors)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun MonthHeader(context: Context, month: YearMonth, colors: WidgetColors) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WidgetText(
                "‹", colors.accent, 20.sp, bold = true,
                modifier = GlanceModifier.padding(horizontal = 12.dp)
                    .clickable(actionRunCallback<MonthStepAction>(actionParametersOf(MonthStepAction.DELTA to -1)))
                    .semantics { contentDescription = context.getString(R.string.calendar_prev_month) },
            )
            WidgetText(
                CalendarText.month(month), colors.text, 14.sp, bold = true, align = TextAlign.Center,
                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(CalendarWidgets.showDay(context, month.atDay(1)))),
            )
            WidgetText(
                "›", colors.accent, 20.sp, bold = true,
                modifier = GlanceModifier.padding(horizontal = 12.dp)
                    .clickable(actionRunCallback<MonthStepAction>(actionParametersOf(MonthStepAction.DELTA to 1)))
                    .semantics { contentDescription = context.getString(R.string.calendar_next_month) },
            )
        }
    }

    /**
     * One day: its number (in a filled pill when today), then a label per item, as many as fit.
     * Weight, not fill: a cell that fills its row's width would push the other six out.
     */
    @Composable
    private fun RowScope.DayCell(context: Context, grid: MonthGridState, day: LocalDate, colors: WidgetColors) {
        val items = grid.entries[day].orEmpty()
        val isToday = day == grid.today
        val description = context.resources.getQuantityString(
            R.plurals.calendar_day_items, items.size, RelativeText.date(context, day, grid.today), items.size,
        )
        Column(
            GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)
                .clickable(actionStartActivity(CalendarWidgets.showDay(context, day)))
                .semantics { contentDescription = description },
        ) {
            Box(GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    GlanceModifier.padding(horizontal = 4.dp).cornerRadius(8.dp)
                        .then(if (isToday) GlanceModifier.background(ColorProvider(Color(colors.accent))) else GlanceModifier),
                ) {
                    val numberColor = when {
                        isToday -> colors.onAccent
                        day.month == grid.month.month -> colors.text
                        else -> colors.muted
                    }
                    WidgetText(day.dayOfMonth.toString(), numberColor, 11.sp, bold = isToday, align = TextAlign.Center)
                }
            }
            // With too many to show, the last slot says how many more there are.
            val shown = if (items.size > grid.labels) (grid.labels - 1).coerceAtLeast(0) else items.size
            items.take(shown).forEach { ItemLabel(context, it) }
            if (items.size > shown && grid.labels > 0) {
                WidgetText("+${items.size - shown}", colors.muted, 9.sp, GlanceModifier.fillMaxWidth(), align = TextAlign.Center)
            }
        }
    }

    /** A day's item: its title on its kind's colour. The title says what it is, not the colour alone. */
    @Composable
    private fun ItemLabel(context: Context, entry: CalendarEntry) {
        Box(GlanceModifier.fillMaxWidth().padding(top = 1.dp)) {
            WidgetText(
                EntryText.title(context, entry), LABEL_TEXT, 9.sp,
                GlanceModifier.fillMaxWidth().cornerRadius(3.dp).background(ColorProvider(Color(labelColor(entry.kind))))
                    .padding(horizontal = 2.dp),
            )
        }
    }

    companion object {
        val KEY_MONTH = stringPreferencesKey("calendar_month")
        private const val WEEKS = 6
        private const val DAYS_PER_WEEK = 7

        /** Most labels in one day, however tall the widget. */
        private const val MAX_LABELS = 5

        /** Header, weekday names and padding, which the six weeks share the rest with. */
        private val CHROME_HEIGHT = 56.dp
        private val DAY_NUMBER_HEIGHT = 16.dp
        private val LABEL_HEIGHT = 14.dp

        /** All three label colours take white text at 4.5:1 or better. */
        private val LABEL_TEXT = Color.White.toArgb()

        fun labelColor(kind: EntryKind): Int = when (kind) {
            EntryKind.EVENT -> Accent
            EntryKind.REMINDER -> Teal
            EntryKind.ALARM -> Brass
        }.toArgb()
    }
}

/** What the grid draws from. [labels]: how many item labels fit in a day cell. */
private data class MonthGridState(
    val month: YearMonth,
    val today: LocalDate,
    val entries: Map<LocalDate, List<CalendarEntry>>,
    val labels: Int,
)

/** ‹ and ›: turn the widget's month, without opening the app. */
class MonthStepAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[DELTA] ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            val month = prefs[MonthWidget.KEY_MONTH]?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: YearMonth.now()
            prefs[MonthWidget.KEY_MONTH] = month.plusMonths(delta.toLong()).toString()
        }
        MonthWidget().update(context, glanceId)
    }

    companion object {
        val DELTA = ActionParameters.Key<Int>("delta")
    }
}
