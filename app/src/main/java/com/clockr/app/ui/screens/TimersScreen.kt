package com.clockr.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.clockr.app.domain.BlockKind
import com.clockr.app.domain.LibraryItem
import com.clockr.app.ui.theme.ClockrTheme
import com.clockr.app.ui.timers.NEW_CHAIN_ROUTE_ID
import com.clockr.app.ui.timers.NEW_TIMER_ROUTE_ID
import com.clockr.app.ui.timers.TimerLibraryViewModel
import com.clockr.app.util.formatDuration

@Composable
fun TimersScreen(modifier: Modifier = Modifier, onBlockClick: (String) -> Unit = {}) {
    val viewModel: TimerLibraryViewModel = viewModel()
    val items by viewModel.items.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

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
        if (items.isEmpty()) {
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
                item { Text("Library", style = MaterialTheme.typography.titleLarge) }
                items(items) { block -> BlockCard(block, onClick = { onBlockClick(block.id) }) }
            }
        }
    }
}

@Composable
private fun BlockCard(block: LibraryItem, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(block.label, style = MaterialTheme.typography.titleLarge)
            Text(
                "${if (block.kind == BlockKind.CHAIN) "Chain" else "Timer"} · ${formatDuration(block.totalSeconds)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TimersScreenPreview() {
    ClockrTheme { TimersScreen() }
}
