package com.relay.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.relay.app.R
import com.relay.app.appContainer
import com.relay.app.data.clock.WidgetConfigEntity
import com.relay.app.ui.clock.CityPickerDialog
import com.relay.app.ui.clock.ClockStyleEditor
import com.relay.app.ui.clock.ColorChoice
import com.relay.app.ui.common.OptionChips
import com.relay.app.ui.theme.RelayTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Set up one widget (spec §09): shown when it's placed, and again from the launcher's
 * "Widget settings". Each placed widget has its own row, so every widget can look different.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out of first placement must not leave a half-made widget behind.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        enableEdgeToEdge()
        val container = appContainer
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            var spec by remember { mutableStateOf<WidgetSpec?>(null) }
            LaunchedEffect(Unit) { spec = WidgetSpec.decode(container.clocks.widget(appWidgetId)?.spec) }
            val s = settings ?: return@setContent
            RelayTheme(s.theme) {
                Surface(Modifier.fillMaxSize()) {
                    spec?.let { current ->
                        WidgetConfigScreen(
                            current, s.clockFormat24h,
                            onChange = { spec = it },
                            onCancel = { finish() },
                            onSave = { save(appWidgetId, current) },
                        )
                    }
                }
            }
        }
    }

    private fun save(appWidgetId: Int, spec: WidgetSpec) = lifecycleScope.launch {
        val json = WidgetSpec.encode(spec)
        appContainer.clocks.saveWidget(WidgetConfigEntity(appWidgetId, json))
        ClockWidget.apply(this@WidgetConfigActivity, appWidgetId, json)
        WidgetTicker.schedule(this@WidgetConfigActivity)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}

@Composable
private fun WidgetConfigScreen(spec: WidgetSpec, is24h: Boolean, onChange: (WidgetSpec) -> Unit, onCancel: () -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    var pickingCity by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    val preview = remember(spec, is24h, now / 60_000) { WidgetRenderer.render(context, spec, 300f, 140f, is24h, now) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, stringResource(R.string.action_cancel)) }
            Text(stringResource(R.string.widget_settings), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = onSave) { Text(stringResource(R.string.action_save)) }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(preview.asImageBitmap(), contentDescription = null, modifier = Modifier.width(300.dp).height(140.dp))
        }
        Text(stringResource(R.string.widget_location), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickingCity = true }) {
                Text(spec.city ?: stringResource(R.string.widget_location_local))
            }
            if (spec.zoneId != null) {
                OutlinedButton(onClick = { onChange(spec.copy(zoneId = null, city = null)) }) { Text(stringResource(R.string.widget_location_local)) }
            }
        }

        HorizontalDivider()
        ClockStyleEditor(spec.style) { onChange(spec.copy(style = it)) }

        HorizontalDivider()
        OptionChips(
            stringResource(R.string.widget_background), WidgetBackground.entries, spec.background,
            label = {
                stringResource(
                    when (it) {
                        WidgetBackground.FOLLOW_THEME -> R.string.widget_bg_theme
                        WidgetBackground.TRANSPARENT -> R.string.widget_bg_transparent
                        WidgetBackground.SOLID -> R.string.widget_bg_solid
                        WidgetBackground.GRADIENT -> R.string.widget_bg_gradient
                    },
                )
            },
            onSelect = { onChange(spec.copy(background = it)) },
        )
        if (spec.background == WidgetBackground.SOLID || spec.background == WidgetBackground.GRADIENT) {
            ColorChoice(stringResource(R.string.widget_bg_color), spec.backgroundColor) { onChange(spec.copy(backgroundColor = it)) }
        }
        if (spec.background == WidgetBackground.GRADIENT) {
            ColorChoice(stringResource(R.string.widget_bg_color2), spec.backgroundColor2) { onChange(spec.copy(backgroundColor2 = it)) }
        }
        OptionChips(
            stringResource(R.string.widget_corners), WidgetCorners.entries, spec.corners,
            label = {
                stringResource(
                    when (it) {
                        WidgetCorners.SHARP -> R.string.widget_corners_sharp
                        WidgetCorners.ROUNDED -> R.string.widget_corners_rounded
                        WidgetCorners.PILL -> R.string.widget_corners_pill
                    },
                )
            },
            onSelect = { onChange(spec.copy(corners = it)) },
        )
        OptionChips(
            stringResource(R.string.widget_border), listOf(0, 1, 2, 4), spec.borderWidthDp,
            label = { if (it == 0) stringResource(R.string.option_off) else "$it dp" },
            onSelect = { onChange(spec.copy(borderWidthDp = it)) },
        )
        if (spec.borderWidthDp > 0) {
            ColorChoice(stringResource(R.string.widget_border_color), spec.borderColor) { onChange(spec.copy(borderColor = it)) }
        }
    }

    if (pickingCity) {
        CityPickerDialog(stringResource(R.string.widget_location), onDismiss = { pickingCity = false }) {
            onChange(spec.copy(zoneId = it.zoneId, city = it.name)); pickingCity = false
        }
    }
}
