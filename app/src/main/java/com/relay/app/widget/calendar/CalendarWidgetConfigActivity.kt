package com.relay.app.widget.calendar

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.relay.app.R
import com.relay.app.appContainer
import com.relay.app.data.clock.WidgetConfigEntity
import com.relay.app.ui.common.OptionChips
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
            LaunchedEffect(Unit) { spec = CalendarWidgetSpec.decode(container.clocks.widget(appWidgetId)?.spec) }
            val s = settings ?: return@setContent
            RelayTheme(s.theme) {
                Surface(Modifier.fillMaxSize()) {
                    spec?.let { current ->
                        CalendarWidgetConfigScreen(
                            current, onChange = { spec = it }, onCancel = { finish() }, onSave = { save(appWidgetId, current) },
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
        OptionChips(
            stringResource(R.string.widget_calendar_show), listOf(false, true), spec.showAll,
            label = { stringResource(if (it) R.string.calendar_filter_all else R.string.calendar_filter_mine) },
            onSelect = { onChange(spec.copy(showAll = it)) },
        )
        HorizontalDivider()
        WidgetFrameEditor(spec.frame) { onChange(spec.withFrame(it)) }
    }
}
