package com.clockr.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clockr.app.data.AlarmEntity
import com.clockr.app.ui.alarms.AlarmsViewModel
import com.clockr.app.ui.alarms.NEW_ALARM_ROUTE_ID
import com.clockr.app.ui.theme.ClockrTheme
import com.clockr.app.util.formatDays
import com.clockr.app.util.formatTime

@Composable
fun AlarmsScreen(modifier: Modifier = Modifier, onAlarmClick: (String) -> Unit = {}) {
    val viewModel: AlarmsViewModel = viewModel()
    val alarms by viewModel.alarms.collectAsState()

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { onAlarmClick(NEW_ALARM_ROUTE_ID) }) {
                Icon(Icons.Default.Add, contentDescription = "New alarm")
            }
        },
    ) { padding ->
        if (alarms.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No alarms yet — tap + to add one",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(alarms, key = { it.id }) { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        onClick = { onAlarmClick(alarm.id) },
                        onEnabledChange = { viewModel.setEnabled(alarm, it) },
                        onDoneToggle = { viewModel.setDone(alarm, !alarm.done) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: AlarmEntity,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDoneToggle: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(formatTime(alarm.hour, alarm.minute), style = MaterialTheme.typography.displayLarge)
                if (alarm.label.isNotBlank()) {
                    Text(alarm.label, style = MaterialTheme.typography.bodyLarge)
                }
                val summary = when {
                    alarm.isRepeating -> formatDays(alarm.repeatDaySet())
                    alarm.done -> "Done"
                    else -> "Once"
                }
                Text(summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (!alarm.isRepeating && alarm.done) {
                    AssistChip(onClick = onDoneToggle, label = { Text("Done — reactivate") })
                } else {
                    Switch(checked = alarm.enabled, onCheckedChange = onEnabledChange)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AlarmsScreenPreview() {
    ClockrTheme { AlarmsScreen() }
}
