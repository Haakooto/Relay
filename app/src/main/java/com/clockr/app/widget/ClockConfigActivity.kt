@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.clockr.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.clockr.app.R
import com.clockr.app.data.ClockConfigEntity
import com.clockr.app.data.ClockrDatabase
import com.clockr.app.ui.clock.ClockStyleFields
import com.clockr.app.ui.clock.ClockStyleState
import com.clockr.app.ui.clock.toStyleState
import com.clockr.app.ui.clock.withStyle
import com.clockr.app.ui.theme.ClockrTheme
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The standard Android app-widget-configure handshake: the launcher (a different app) starts
 * this Activity with [AppWidgetManager.EXTRA_APPWIDGET_ID] whenever a widget is first placed, or
 * later reconfigured via its "Widget settings" long-press option. One [ClockConfigEntity] row is
 * dedicated to this appWidgetId — independent from every other widget instance and from any
 * in-app clock, per spec.
 */
class ClockConfigActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(Activity.RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val dao = ClockrDatabase.get(this).clockConfigDao()

        setContent {
            ClockrTheme {
                var state by remember { mutableStateOf<ClockStyleState?>(null) }
                LaunchedEffect(Unit) {
                    val existing = dao.findByAppWidgetId(appWidgetId)
                    state = (existing ?: ClockConfigEntity.default(id = UUID.randomUUID().toString(), appWidgetId = appWidgetId)).toStyleState()
                }
                val current = state
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.clock_config_widget_title)) },
                            actions = {
                                TextButton(onClick = {
                                    val toSave = current ?: return@TextButton
                                    lifecycleScope.launch {
                                        val entity = (dao.findByAppWidgetId(appWidgetId)
                                            ?: ClockConfigEntity.default(id = UUID.randomUUID().toString(), appWidgetId = appWidgetId))
                                            .withStyle(toSave)
                                        dao.upsert(entity)
                                        val glanceId = runCatching {
                                            androidx.glance.appwidget.GlanceAppWidgetManager(this@ClockConfigActivity).getGlanceIdBy(appWidgetId)
                                        }.getOrNull()
                                        if (glanceId != null) ClockrGlanceWidget().update(this@ClockConfigActivity, glanceId)
                                        setResult(Activity.RESULT_OK, android.content.Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                                        finish()
                                    }
                                }) { Text(stringResource(R.string.action_save)) }
                            },
                        )
                    },
                ) { padding ->
                    if (current == null) {
                        CircularProgressIndicator(modifier = Modifier.padding(padding))
                    } else {
                        LazyColumn(
                            modifier = Modifier.padding(padding).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            item {
                                ClockStyleFieldsHost(current) { state = it }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** LazyColumn items can't each be a totally free-form multi-composable block cleanly without
 *  wrapping — this just forwards into [ClockStyleFields] inside one item's Column. */
@Composable
private fun ClockStyleFieldsHost(state: ClockStyleState, onChange: (ClockStyleState) -> Unit) {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ClockStyleFields(state = state, onChange = onChange, showWidgetFields = true)
    }
}
