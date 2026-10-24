package com.relay.app.widget.calendar

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
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
import com.relay.app.event.GRID_DAYS
import com.relay.app.ui.calendar.CalendarText
import com.relay.app.util.AppLocale
import com.relay.app.util.RelativeText
import com.relay.app.widget.WidgetTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

/**
 * The month widget (spec §10b, #15): the pane's grid in small. The arrows and day taps change the
 * widget in place; the month title opens the Calendar pane there. Tall enough, the selected day's
 * entries list under the grid.
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
        val selected = prefs[KEY_SELECTED]?.let(LocalDate::ofEpochDay)?.takeIf { YearMonth.from(it) == month }
            ?: if (YearMonth.from(today) == month) today else month.atDay(1)
        val settings = widgetSettings(context)
        val gridStart = CalendarViews.gridStart(month, settings?.firstDayOfWeek ?: DayOfWeek.MONDAY)
        val entries = calendarEntries(context, gridStart, gridStart.plusDays(GRID_DAYS - 1), spec.showAll)
        val is24h = settings?.clockFormat24h ?: true
        val grid = MonthGridState(month, selected, today, gridStart, entries.orEmpty(), is24h)
        val showList = LocalSize.current.height >= LIST_MIN_HEIGHT

        Box(GlanceModifier.fillMaxSize()) {
            FrameBackground(context, spec.frame)
            Column(GlanceModifier.fillMaxSize().padding(8.dp)) {
                MonthHeader(context, grid, colors)
                Row(GlanceModifier.fillMaxWidth()) {
                    for (d in 0 until DAYS_PER_WEEK) {
                        WidgetText(
                            gridStart.plusDays(d.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, AppLocale.formatting),
                            colors.muted, 11.sp, GlanceModifier.defaultWeight(), align = TextAlign.Center,
                        )
                    }
                }
                for (week in 0 until WEEKS) {
                    val rowHeight = if (showList) GlanceModifier.height(CELL_HEIGHT) else GlanceModifier.defaultWeight()
                    Row(GlanceModifier.fillMaxWidth().then(rowHeight)) {
                        for (d in 0 until DAYS_PER_WEEK) DayCell(grid, gridStart.plusDays(week * DAYS_PER_WEEK + d.toLong()), colors)
                    }
                }
                if (showList) DayList(context, grid, colors)
            }
        }
    }

    @Composable
    private fun MonthHeader(context: Context, grid: MonthGridState, colors: WidgetColors) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WidgetText(
                "‹", colors.accent, 20.sp, bold = true,
                modifier = GlanceModifier.padding(horizontal = 10.dp)
                    .clickable(actionRunCallback<MonthStepAction>(actionParametersOf(MonthStepAction.DELTA to -1)))
                    .semantics { contentDescription = context.getString(R.string.calendar_prev_month) },
            )
            WidgetText(
                CalendarText.month(grid.month), colors.text, 15.sp, bold = true, align = TextAlign.Center,
                modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(CalendarWidgets.showDay(context, grid.selected))),
            )
            WidgetText(
                "›", colors.accent, 20.sp, bold = true,
                modifier = GlanceModifier.padding(horizontal = 10.dp)
                    .clickable(actionRunCallback<MonthStepAction>(actionParametersOf(MonthStepAction.DELTA to 1)))
                    .semantics { contentDescription = context.getString(R.string.calendar_next_month) },
            )
        }
    }

    /** A day: its number (filled when selected, bold when today) over up to three dots. */
    @Composable
    private fun androidx.glance.layout.RowScope.DayCell(grid: MonthGridState, day: LocalDate, colors: WidgetColors) {
        val isSelected = day == grid.selected
        val count = grid.entries[day]?.size ?: 0
        val textColor = when {
            isSelected -> colors.onAccent
            day.month == grid.month.month -> colors.text
            else -> colors.muted
        }
        Column(
            GlanceModifier.defaultWeight().fillMaxSize()
                .clickable(actionRunCallback<SelectDayAction>(actionParametersOf(SelectDayAction.DAY to day.toEpochDay()))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                GlanceModifier.size(DAY_SIZE).cornerRadius(DAY_SIZE / 2)
                    .then(if (isSelected) GlanceModifier.background(ColorProvider(Color(colors.accent))) else GlanceModifier),
                contentAlignment = Alignment.Center,
            ) {
                WidgetText(day.dayOfMonth.toString(), textColor, 12.sp, bold = day == grid.today, align = TextAlign.Center)
            }
            WidgetText("•".repeat(minOf(count, MAX_DOTS)), colors.accent, 8.sp, align = TextAlign.Center)
        }
    }

    /** One child of the outer column: Glance allows at most ten children per row or column. */
    @Composable
    private fun androidx.glance.layout.ColumnScope.DayList(context: Context, grid: MonthGridState, colors: WidgetColors) {
        val entries = grid.entries[grid.selected].orEmpty()
        Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
            WidgetText(
                RelativeText.date(context, grid.selected, grid.today), colors.accent, 12.sp, bold = true,
                modifier = GlanceModifier.padding(top = 6.dp, bottom = 2.dp),
            )
            if (entries.isEmpty()) WidgetText(context.getString(R.string.calendar_day_empty), colors.muted, 13.sp)
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(entries) { entry -> EntryRow(context, entry, grid.is24h, colors) }
            }
        }
    }

    @Composable
    private fun EntryRow(context: Context, entry: CalendarEntry, is24h: Boolean, colors: WidgetColors) {
        Row(
            GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(actionStartActivity(CalendarWidgets.open(context, entry))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetText(EntryText.time(context, entry, is24h), colors.muted, 13.sp, GlanceModifier.width(TIME_WIDTH))
            WidgetText(EntryText.title(context, entry), colors.text, 14.sp, GlanceModifier.defaultWeight())
        }
    }

    companion object {
        val KEY_MONTH = stringPreferencesKey("calendar_month")
        val KEY_SELECTED = longPreferencesKey("calendar_selected")
        private const val WEEKS = 6
        private const val DAYS_PER_WEEK = 7
        private const val MAX_DOTS = 3
        private val DAY_SIZE = 24.dp
        private val CELL_HEIGHT = 38.dp
        private val TIME_WIDTH = 92.dp

        /** Below this the selected day's list doesn't fit under the grid. */
        private val LIST_MIN_HEIGHT = 330.dp
    }
}

/** What the grid draws from. */
private data class MonthGridState(
    val month: YearMonth,
    val selected: LocalDate,
    val today: LocalDate,
    val gridStart: LocalDate,
    val entries: Map<LocalDate, List<CalendarEntry>>,
    val is24h: Boolean,
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

/** A day tap: select it (and its month, for a day at the grid's edge). */
class SelectDayAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val day = parameters[DAY]?.let(LocalDate::ofEpochDay) ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[MonthWidget.KEY_SELECTED] = day.toEpochDay()
            prefs[MonthWidget.KEY_MONTH] = YearMonth.from(day).toString()
        }
        MonthWidget().update(context, glanceId)
    }

    companion object {
        val DAY = ActionParameters.Key<Long>("day")
    }
}
