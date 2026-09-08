package com.clockr.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clockr.app.ui.theme.ClockrTheme

private data class StopwatchPreview(val name: String, val elapsed: String, val laps: Int)

private val sampleStopwatches = listOf(
    StopwatchPreview("Interval run", "00:18:42", laps = 3),
    StopwatchPreview("(unnamed, t₀ 10:00)", "00:12:05", laps = 0),
)

@Composable
fun StopwatchScreen(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(sampleStopwatches) { sw -> StopwatchCard(sw) }
        }
    }
}

@Composable
private fun StopwatchCard(sw: StopwatchPreview) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(sw.name, style = MaterialTheme.typography.bodyLarge)
            Text(sw.elapsed, style = MaterialTheme.typography.displayLarge)
            Text(
                "${sw.laps} laps",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StopwatchScreenPreview() {
    ClockrTheme { StopwatchScreen() }
}
