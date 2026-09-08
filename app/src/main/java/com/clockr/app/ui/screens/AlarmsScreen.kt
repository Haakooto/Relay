package com.clockr.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clockr.app.ui.theme.ClockrTheme

private data class AlarmPreview(
    val time: String,
    val label: String,
    val repeatSummary: String,
    val enabled: Boolean,
)

private val sampleAlarms = listOf(
    AlarmPreview("06:30", "Weekday wake-up", "Mon–Fri", enabled = true),
    AlarmPreview("08:00", "Slow Saturday", "Sat, Sun", enabled = false),
    AlarmPreview("13:15", "Dentist", "Once · delete after firing", enabled = true),
)

@Composable
fun AlarmsScreen(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(sampleAlarms) { alarm -> AlarmCard(alarm) }
        }
    }
}

@Composable
private fun AlarmCard(alarm: AlarmPreview) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(alarm.time, style = MaterialTheme.typography.displayLarge)
                Text(alarm.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    alarm.repeatSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = alarm.enabled, onCheckedChange = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AlarmsScreenPreview() {
    ClockrTheme { AlarmsScreen() }
}
