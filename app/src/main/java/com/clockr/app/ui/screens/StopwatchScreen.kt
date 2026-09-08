package com.clockr.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.clockr.app.ui.theme.ClockrTheme

private enum class StopwatchState { RUNNING, PAUSED }

/**
 * Mockup stand-in for the Stopwatch entity (spec §03/§05): stays in the list — running or
 * paused — until explicitly removed. Not tied to being "running" to exist.
 */
private data class StopwatchPreview(
    val name: String?,
    val t0: String,
    val elapsed: String,
    val state: StopwatchState,
    val laps: Int,
)

private val sampleStopwatches = listOf(
    StopwatchPreview("Interval run", t0 = "00:00", elapsed = "00:18:42", state = StopwatchState.RUNNING, laps = 3),
    StopwatchPreview(null, t0 = "10:00", elapsed = "00:22:05", state = StopwatchState.PAUSED, laps = 0),
    StopwatchPreview("Sourdough proof", t0 = "00:00", elapsed = "01:04:11", state = StopwatchState.PAUSED, laps = 1),
)

@Composable
fun StopwatchScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = {}) { Icon(Icons.Default.Add, contentDescription = "New stopwatch") }
        },
    ) { padding ->
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(sw.name ?: "Unnamed", style = MaterialTheme.typography.bodyLarge)
                if (sw.t0 != "00:00") {
                    Text("t₀ ${sw.t0}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(sw.elapsed, style = MaterialTheme.typography.displayLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${sw.laps} laps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    IconButton(onClick = {}, enabled = sw.state == StopwatchState.RUNNING) {
                        Icon(Icons.Default.Flag, contentDescription = "Lap")
                    }
                    IconButton(onClick = {}) {
                        Icon(
                            if (sw.state == StopwatchState.RUNNING) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (sw.state == StopwatchState.RUNNING) "Pause" else "Resume",
                        )
                    }
                    IconButton(onClick = {}) { Icon(Icons.Default.Replay, contentDescription = "Restart") }
                    IconButton(onClick = {}) { Icon(Icons.Default.Close, contentDescription = "Delete") }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StopwatchScreenPreview() {
    ClockrTheme { StopwatchScreen() }
}
