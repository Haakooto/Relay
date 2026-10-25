package com.relay.app.widget.calendar

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
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
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import com.relay.app.R
import com.relay.app.event.CalendarEntry
import com.relay.app.event.CalendarViews
import com.relay.app.event.DAYS_PER_WEEK
import com.relay.app.event.GRID_DAYS
import com.relay.app.event.SpanSegment
import com.relay.app.ui.calendar.CalendarText
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
        val entries = calendarEntries(context, gridStart, gridStart.plusDays(GRID_DAYS - 1), spec).orEmpty()
        // Glance has no spanning cells, so bars are sized from the widget's own width.
        val size = LocalSize.current
        val cellWidth = (size.width - PADDING * 2) / DAYS_PER_WEEK
        val grid = MonthGridState(month, today, entries, cellWidth, (size.height - CHROME_HEIGHT) / WEEKS)

        Box(GlanceModifier.fillMaxSize()) {
            FrameBackground(context, spec.frame)
            Column(GlanceModifier.fillMaxSize().padding(PADDING)) {
                MonthHeader(context, month, colors)
                Row(GlanceModifier.fillMaxWidth()) {
                    for (d in 0 until DAYS_PER_WEEK) {
                        WidgetText(
                            gridStart.plusDays(d.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, AppLocale.formatting),
                            colors.muted, 10.sp, GlanceModifier.defaultWeight(), align = TextAlign.Center,
                        )
                    }
                }
                for (week in 0 until WEEKS) Week(context, grid, gridStart.plusDays(week * DAYS_PER_WEEK.toLong()), colors)
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
     * One week: the day numbers, then up to [MAX_LANES] lanes of multi-day bars (#35), then each
     * day's other items as labels, as many as the height leaves room for. Four children, well
     * within Glance's ten per column.
     */
    @Composable
    private fun ColumnScope.Week(context: Context, grid: MonthGridState, weekStart: LocalDate, colors: WidgetColors) {
        val lanes = CalendarViews.weekLanes(weekStart, grid.entries, MAX_BARS_PER_LANE)
        val shown = lanes.take(MAX_LANES)
        // Bars that don't fit still count, in their days' "+n".
        val hidden = lanes.drop(MAX_LANES).flatten()
        val labels = ((grid.weekHeight - DAY_NUMBER_HEIGHT - LANE_HEIGHT * shown.size) / LABEL_HEIGHT).toInt().coerceIn(0, MAX_LABELS)
        val days = (0 until DAYS_PER_WEEK).map { weekStart.plusDays(it.toLong()) }
        Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
            Row(GlanceModifier.fillMaxWidth()) { days.forEach { DayNumber(context, grid, it, colors) } }
            shown.forEach { Lane(context, it, grid.cellWidth) }
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                days.forEachIndexed { col, day ->
                    DayLabels(context, grid, day, labels, hidden.count { col in it.startCol..it.endCol }, colors)
                }
            }
        }
    }

    /** A day's number, in a filled pill when it's today. Weight, not fill: filling would push the other six out. */
    @Composable
    private fun RowScope.DayNumber(context: Context, grid: MonthGridState, day: LocalDate, colors: WidgetColors) {
        val count = grid.entries[day]?.size ?: 0
        val isToday = day == grid.today
        val description = context.resources.getQuantityString(
            R.plurals.calendar_day_items, count, RelativeText.date(context, day, grid.today), count,
        )
        Box(
            GlanceModifier.defaultWeight().clickable(actionStartActivity(CalendarWidgets.showDay(context, day)))
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
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
    }

    /** One lane of multi-day bars, each as wide as the days it covers. */
    @Composable
    private fun Lane(context: Context, lane: List<SpanSegment>, cellWidth: Dp) {
        Row(GlanceModifier.fillMaxWidth().padding(top = 1.dp)) {
            var col = 0
            lane.forEach { segment ->
                if (segment.startCol > col) Spacer(GlanceModifier.width(cellWidth * (segment.startCol - col)))
                Box(GlanceModifier.width(cellWidth * (segment.endCol - segment.startCol + 1)).padding(horizontal = 1.dp)) {
                    val open = actionStartActivity(CalendarWidgets.openItem(context, segment.entry.id))
                    ItemLabel(context, segment.entry, GlanceModifier.clickable(open))
                }
                col = segment.endCol + 1
            }
        }
    }

    /** A day's single-day items as labels; with too many, the last slot says how many more. */
    @Composable
    private fun RowScope.DayLabels(
        context: Context,
        grid: MonthGridState,
        day: LocalDate,
        room: Int,
        hiddenBars: Int,
        colors: WidgetColors,
    ) {
        val items = grid.entries[day].orEmpty().filter { !it.isMultiDay }
        val total = items.size + hiddenBars
        val shown = if (total > room) (room - 1).coerceAtLeast(0).coerceAtMost(items.size) else items.size
        Column(
            GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)
                .clickable(actionStartActivity(CalendarWidgets.showDay(context, day))),
        ) {
            items.take(shown).forEach { Box(GlanceModifier.fillMaxWidth().padding(top = 1.dp)) { ItemLabel(context, it) } }
            if (total > shown && room > 0) {
                WidgetText("+${total - shown}", colors.muted, 9.sp, GlanceModifier.fillMaxWidth(), align = TextAlign.Center)
            }
        }
    }

    /** An item's title on its colour (#34). The title says what it is, not the colour alone. */
    @Composable
    private fun ItemLabel(context: Context, entry: CalendarEntry, modifier: GlanceModifier = GlanceModifier) {
        WidgetText(
            EntryText.title(context, entry), LABEL_TEXT, 9.sp,
            modifier.fillMaxWidth().cornerRadius(3.dp).background(ColorProvider(Color(entry.color.toInt()))).padding(horizontal = 2.dp),
        )
    }

    companion object {
        val KEY_MONTH = stringPreferencesKey("calendar_month")
        private const val WEEKS = 6

        /** Most labels in one day, however tall the widget. */
        private const val MAX_LABELS = 5

        /** Lanes of multi-day bars per week; more go into "+n". */
        private const val MAX_LANES = 2

        /** Bars in one lane: each takes a spacer and a box, and a Glance row holds ten. */
        private const val MAX_BARS_PER_LANE = 4
        private val PADDING = 6.dp

        /** Header, weekday names and padding, which the six weeks share the rest with. */
        private val CHROME_HEIGHT = 56.dp
        private val DAY_NUMBER_HEIGHT = 16.dp
        private val LANE_HEIGHT = 14.dp
        private val LABEL_HEIGHT = 14.dp

        /** The calendar palette takes white text at 4.5:1 or better. */
        private val LABEL_TEXT = Color.White.toArgb()
    }
}

/** What the grid draws from, with one day column's width and one week's height. */
private data class MonthGridState(
    val month: YearMonth,
    val today: LocalDate,
    val entries: Map<LocalDate, List<CalendarEntry>>,
    val cellWidth: Dp,
    val weekHeight: Dp,
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
