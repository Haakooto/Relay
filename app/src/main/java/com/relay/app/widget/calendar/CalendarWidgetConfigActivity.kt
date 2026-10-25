package com.relay.app.widget.calendar

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.relay.app.R
import com.relay.app.appContainer
import com.relay.app.data.clock.WidgetConfigEntity
import com.relay.app.data.event.CalendarEntity
import com.relay.app.event.PhoneCalendar
import com.relay.app.event.phoneCalendarKey
import com.relay.app.ui.calendar.calendarName
import com.relay.app.ui.theme.RelayTheme
import com.relay.app.widget.WidgetFrameEditor
import kotlinx.coroutines.launch

/**
 * Set up one calendar widget (spec §10b): what it shows and its frame. Shown when it's placed and
 * from the launcher's "Widget settings". Backing out of first placement leaves no widget.
 */
class CalendarWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        enableEdgeToEdge()
        val container = appContainer
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            var spec by remember { mutableStateOf<CalendarWidgetSpec?>(null) }
            val calendars by container.calendars.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
            // The phone's calendars (#16), when Relay may read them.
            val phoneCalendars by produceState(emptyList<PhoneCalendar>()) { value = container.phoneCalendars.calendars() }
            LaunchedEffect(Unit) {
                val stored = CalendarWidgetSpec.decode(container.clocks.widget(appWidgetId)?.spec)
                // Spell out what an older widget showed, so its checkboxes start right.
                spec = stored.copy(calendarIds = stored.selection(container.calendars.all()).shown.toList())
            }
            val s = settings ?: return@setContent
            RelayTheme(s.theme) {
                Surface(Modifier.fillMaxSize()) {
                    spec?.let { current ->
                        CalendarWidgetConfigScreen(
                            current, calendars, phoneCalendars, onChange = { spec = it }, onCancel = { finish() },
                            onSave = { save(appWidgetId, current) },
                        )
                    }
                }
            }
        }
    }

    private fun save(appWidgetId: Int, spec: CalendarWidgetSpec) = lifecycleScope.launch {
        appContainer.clocks.saveWidget(WidgetConfigEntity(appWidgetId, CalendarWidgetSpec.encode(spec)))
        CalendarWidgets.refreshAll(this@CalendarWidgetConfigActivity)
        CalendarWidgetTicker.schedule(this@CalendarWidgetConfigActivity)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}

@Composable
private fun CalendarWidgetConfigScreen(
    spec: CalendarWidgetSpec,
    calendars: List<CalendarEntity>,
    phoneCalendars: List<PhoneCalendar>,
    onChange: (CalendarWidgetSpec) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, stringResource(R.string.action_cancel)) }
            Text(stringResource(R.string.widget_settings), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = onSave) { Text(stringResource(R.string.action_save)) }
        }
        Text(stringResource(R.string.widget_calendar_show), style = MaterialTheme.typography.bodyLarge)
        val shown = spec.calendarIds.orEmpty().toSet()
        val rows = calendars.map { Triple(it.id, it.color, calendarName(it)) } +
            phoneCalendars.map { Triple(phoneCalendarKey(it.id), it.color, "${it.name} (${it.account})") }
        rows.forEach { (key, color, name) ->
            val on = key in shown
            val toggle = { onChange(spec.copy(calendarIds = (if (on) shown - key else shown + key).toList())) }
            Row(Modifier.fillMaxWidth().clickable(onClick = toggle), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = on, onCheckedChange = { toggle() })
                Box(Modifier.size(14.dp).background(Color(color.toInt()), CircleShape))
                Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
            }
        }
        HorizontalDivider()
        WidgetFrameEditor(spec.frame) { onChange(spec.withFrame(it)) }
    }
}
