package com.clockr.app.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clockr.app.domain.BlockKind
import com.clockr.app.domain.LibraryItem
import com.clockr.app.run.ActiveRunSnapshot
import com.clockr.app.run.ActiveRunState
import com.clockr.app.run.RunPhase
import com.clockr.app.run.TimerRunService
import com.clockr.app.ui.theme.ClockrTheme
import com.clockr.app.ui.timers.NEW_CHAIN_ROUTE_ID
import com.clockr.app.ui.timers.NEW_TIMER_ROUTE_ID
import com.clockr.app.ui.timers.TimerLibraryViewModel
import com.clockr.app.util.formatDuration
import com.clockr.app.util.formatElapsedClock

@Composable
fun TimersScreen(modifier: Modifier = Modifier, onBlockClick: (String) -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: TimerLibraryViewModel = viewModel()
    val items by viewModel.items.collectAsState()
    val activeRun by ActiveRunState.current.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    fun sendServiceAction(action: String) {
        context.startService(Intent(context, TimerRunService::class.java).setAction(action))
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New block")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("New Timer") },
                        onClick = {
                            menuExpanded = false
                            onBlockClick(NEW_TIMER_ROUTE_ID)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("New Chain") },
                        onClick = {
                            menuExpanded = false
                            onBlockClick(NEW_CHAIN_ROUTE_ID)
                        },
                    )
                }
            }
        },
    ) { padding ->
        if (items.isEmpty() && activeRun == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No timers or chains yet — tap + to add one",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                activeRun?.let { run ->
                    item {
                        ActiveRunCard(
                            run = run,
                            onStop = { sendServiceAction(TimerRunService.ACTION_STOP) },
                            onContinue = { sendServiceAction(TimerRunService.ACTION_CONTINUE) },
                            onCompleteStop = { sendServiceAction(TimerRunService.ACTION_COMPLETE_STOP) },
                            onCompleteRestart = { sendServiceAction(TimerRunService.ACTION_COMPLETE_RESTART) },
                            onCompleteAddMinute = { sendServiceAction(TimerRunService.ACTION_COMPLETE_ADD_MINUTE) },
                        )
                    }
                }
                item { Text("Library", style = MaterialTheme.typography.titleLarge) }
                items(items) { block ->
                    BlockCard(
                        block = block,
                        onClick = { onBlockClick(block.id) },
                        onStart = { TimerRunService.start(context, block.id, block.kind) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveRunCard(
    run: ActiveRunSnapshot,
    onStop: () -> Unit,
    onContinue: () -> Unit,
    onCompleteStop: () -> Unit,
    onCompleteRestart: () -> Unit,
    onCompleteAddMinute: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(run.blockLabel, style = MaterialTheme.typography.titleLarge)
            val stepText = run.stepLabel ?: "Step ${run.stepIndex + 1}"
            Text(
                if (run.stepCount > 1) "$stepText (${run.stepIndex + 1}/${run.stepCount})" else stepText,
                style = MaterialTheme.typography.bodyLarge,
            )
            when (run.phase) {
                RunPhase.RUNNING -> {
                    Text(formatElapsedClock(run.stepSeconds), style = MaterialTheme.typography.displayLarge)
                    TextButton(onClick = onStop) { Text("Stop") }
                }
                RunPhase.AWAITING_INTERACTION -> {
                    Text("Done — waiting for you", style = MaterialTheme.typography.bodyLarge)
                    Row {
                        TextButton(onClick = onContinue) { Text("Continue") }
                        TextButton(onClick = onStop) { Text("Stop") }
                    }
                }
                RunPhase.AWAITING_COMPLETION -> {
                    Text("Finished", style = MaterialTheme.typography.bodyLarge)
                    Row {
                        TextButton(onClick = onCompleteStop) { Text("Stop") }
                        TextButton(onClick = onCompleteRestart) { Text("Restart") }
                        TextButton(onClick = onCompleteAddMinute) { Text("+1 min") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockCard(block: LibraryItem, onClick: () -> Unit, onStart: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(block.label, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${if (block.kind == BlockKind.CHAIN) "Chain" else "Timer"} · ${formatDuration(block.totalSeconds)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onStart) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Start")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TimersScreenPreview() {
    ClockrTheme { TimersScreen() }
}
