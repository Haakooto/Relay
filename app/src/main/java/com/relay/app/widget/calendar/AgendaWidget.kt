package com.relay.app.widget.calendar

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.relay.app.R
import com.relay.app.event.AgendaRow
import com.relay.app.event.CalendarViews
import com.relay.app.util.RelativeText
import com.relay.app.widget.WidgetTheme
import java.time.LocalDate

/**
 * The agenda widget (spec §10b, #15): the next seven days as a native, scrollable list. Rows
 * open their item; a day heading opens the Calendar pane on that day; + makes a new event.
 */
class AgendaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Agenda() }
    }

    @Composable
    private fun Agenda() {
        val context = LocalContext.current
        val prefs = currentState<Preferences>()
        prefs[CalendarWidgets.KEY_TICK] // read so a refresh recomposes
        val spec = CalendarWidgetSpec.decode(prefs[CalendarWidgets.KEY_SPEC])
        val colors = WidgetColors.of(spec.frame, WidgetTheme.of(context))
        val today = LocalDate.now()
        val is24h = widgetSettings(context)?.clockFormat24h ?: true
        val entries = calendarEntries(context, today, CalendarViews.agendaEnd(today), spec.showAll)
        Box(GlanceModifier.fillMaxSize()) {
            FrameBackground(context, spec.frame)
            Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    WidgetText(
                        context.getString(R.string.pane_calendar), colors.text, 16.sp, bold = true,
                        modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(CalendarWidgets.showDay(context, today))),
                    )
                    WidgetText(
                        "+", colors.accent, 22.sp, bold = true,
                        modifier = GlanceModifier.padding(horizontal = 8.dp)
                            .clickable(actionStartActivity(CalendarWidgets.newEvent(context, today)))
                            .semantics { contentDescription = context.getString(R.string.calendar_new_event) },
                    )
                }
                val rows = entries?.let { CalendarViews.agenda(it, today) }.orEmpty()
                if (entries != null && rows.isEmpty()) {
                    WidgetText(context.getString(R.string.widget_agenda_empty), colors.muted, 13.sp, GlanceModifier.padding(top = 8.dp))
                }
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(rows) { row -> AgendaRowView(context, row, today, is24h, colors) }
                }
            }
        }
    }

    @Composable
    private fun AgendaRowView(context: Context, row: AgendaRow, today: LocalDate, is24h: Boolean, colors: WidgetColors) {
        when (row) {
            is AgendaRow.Heading -> WidgetText(
                RelativeText.date(context, row.day, today), colors.accent, 12.sp, bold = true,
                modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)
                    .clickable(actionStartActivity(CalendarWidgets.showDay(context, row.day))),
            )
            is AgendaRow.Item -> Row(
                GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clickable(actionStartActivity(CalendarWidgets.open(context, row.entry))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetText(EntryText.time(context, row.entry, is24h), colors.muted, 13.sp, GlanceModifier.width(TIME_WIDTH))
                WidgetText(EntryText.title(context, row.entry), colors.text, 14.sp, GlanceModifier.defaultWeight())
            }
        }
    }

    private companion object {
        val TIME_WIDTH = 92.dp
    }
}
