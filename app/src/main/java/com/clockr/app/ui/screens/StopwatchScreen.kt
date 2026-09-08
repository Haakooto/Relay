package com.clockr.app.ui.screens

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clockr.app.ui.stopwatch.StopwatchUi
import com.clockr.app.ui.stopwatch.StopwatchViewModel
import com.clockr.app.ui.theme.ClockrTheme
import com.clockr.app.util.formatElapsedClock

@Composable
fun StopwatchScreen(modifier: Modifier = Modifier) {
    val viewModel: StopwatchViewModel = viewModel()
    val stopwatches by viewModel.stopwatches.collectAsState()
    val nowMillis by viewModel.nowMillis.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New stopwatch")
            }
        },
    ) { padding ->
        if (stopwatches.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No stopwatches running — tap + to start one",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(stopwatches, key = { it.id }) { sw ->
                    StopwatchCard(
                        sw = sw,
                        nowMillis = nowMillis,
                        onLap = { viewModel.lap(sw.id) },
                        onTogglePause = { viewModel.togglePause(sw.id) },
                        onRestart = { viewModel.restart(sw.id) },
                        onDelete = { viewModel.delete(sw.id) },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddStopwatchDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, t0Minutes ->
                viewModel.add(name, t0Minutes * 60)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun AddStopwatchDialog(onDismiss: () -> Unit, onConfirm: (String, Long) -> Unit) {
    var name by remember { mutableStateOf("") }
    var t0Text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New stopwatch") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (optional)") })
                OutlinedTextField(
                    value = t0Text,
                    onValueChange = { t0Text = it },
                    label = { Text("Start from, minutes (optional)") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, t0Text.toLongOrNull() ?: 0L) }) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StopwatchCard(
    sw: StopwatchUi,
    nowMillis: Long,
    onLap: () -> Unit,
    onTogglePause: () -> Unit,
    onRestart: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(sw.name ?: "Unnamed", style = MaterialTheme.typography.bodyLarge)
            Text(formatElapsedClock(sw.elapsedSeconds(nowMillis)), style = MaterialTheme.typography.displayLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${sw.laps.size} laps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    IconButton(onClick = onLap, enabled = sw.isRunning) {
                        Icon(Icons.Default.Flag, contentDescription = "Lap")
                    }
                    IconButton(onClick = onTogglePause) {
                        Icon(
                            if (sw.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (sw.isRunning) "Pause" else "Resume",
                        )
                    }
                    IconButton(onClick = onRestart) { Icon(Icons.Default.Replay, contentDescription = "Restart") }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Close, contentDescription = "Delete") }
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
