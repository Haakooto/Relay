@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.clockr.app.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.clockr.app.domain.TransitionMode
import com.clockr.app.ui.common.SoundPickerRow
import com.clockr.app.ui.theme.ClockrTheme
import com.clockr.app.ui.timers.BlockConfigViewModel
import com.clockr.app.ui.timers.BlockConfigViewModelFactory
import com.clockr.app.ui.timers.NEW_CHAIN_ROUTE_ID
import com.clockr.app.ui.timers.NEW_TIMER_ROUTE_ID
import com.clockr.app.ui.timers.ResolvedStepUi
import com.clockr.app.util.formatDuration

@Composable
fun BlockConfigScreen(blockId: String, onBack: () -> Unit = {}) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: BlockConfigViewModel = viewModel(
        factory = BlockConfigViewModelFactory(application, blockId),
    )
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.label.ifBlank { "Untitled" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { viewModel.delete(onBack) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    TextButton(
                        enabled = state.error == null && state.labelError == null && !state.loading,
                        onClick = { viewModel.save(onBack) },
                    ) {
                        Text("Save")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            state.notFound -> Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text("Not found", modifier = Modifier.align(Alignment.Center))
            }
            else -> LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = state.label,
                        onValueChange = viewModel::updateLabel,
                        label = { Text("Label") },
                        supportingText = { Text(state.labelError ?: "Letters, digits, underscore — must be unique") },
                        isError = state.labelError != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (state.kind == BlockKind.TIMER) {
                    item {
                        OutlinedTextField(
                            value = state.durationExpr,
                            onValueChange = viewModel::updateDurationExpr,
                            label = { Text("Duration") },
                            supportingText = {
                                Text(state.error ?: state.resolvedSeconds?.let { "= ${formatDuration(it)}" } ?: "")
                            },
                            isError = state.error != null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Persistent — save to library", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = state.persistent, onCheckedChange = viewModel::updatePersistent)
                    }
                }

                item {
                    SoundPickerRow(
                        label = if (state.kind == BlockKind.CHAIN) "Default sound profile" else "Sound profile",
                        soundProfileId = state.soundProfileId,
                        onPicked = viewModel::updateSoundProfileId,
                    )
                }

                if (state.kind == BlockKind.CHAIN) {
                    item { Text("Transition mode", style = MaterialTheme.typography.bodyLarge) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.transitionMode == TransitionMode.REQUIRE_INTERACTION,
                                onClick = { viewModel.updateTransitionMode(TransitionMode.REQUIRE_INTERACTION) },
                                label = { Text("Require interaction") },
                            )
                            FilterChip(
                                selected = state.transitionMode == TransitionMode.SEAMLESS,
                                onClick = { viewModel.updateTransitionMode(TransitionMode.SEAMLESS) },
                                label = { Text("Seamless") },
                            )
                        }
                    }

                    item { HorizontalDivider() }
                    item {
                        var showHelp by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Content", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 4.dp))
                            IconButton(onClick = { showHelp = true }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Syntax help")
                            }
                        }
                        OutlinedTextField(
                            value = state.contentExpr,
                            onValueChange = viewModel::updateContentExpr,
                            supportingText = { Text(state.error ?: "e.g. (Work, Break) or repeat(4, (Work, Break)) — one line per step also works") },
                            isError = state.error != null,
                            minLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (showHelp) {
                            ChainSyntaxHelpDialog(onDismiss = { showHelp = false })
                        }
                    }
                    if (state.error == null) {
                        item { Text("Preview", style = MaterialTheme.typography.titleLarge) }
                        items(state.resolvedSteps) { step -> ResolvedStepRow(step) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResolvedStepRow(step: ResolvedStepUi) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(step.label ?: "—", style = MaterialTheme.typography.bodyLarge)
            Text(
                formatDuration(step.seconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChainSyntaxHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Writing a chain") },
        text = {
            Column {
                Text(
                    "A chain is a list of steps in parentheses, one per line (or comma-separated " +
                        "— whichever's easier):",
                )
                Text("(Work, Break)", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                Text("Each step is one of:")
                Text("• A plain duration: 3, 20m, 2h30m10s", modifier = Modifier.padding(top = 4.dp))
                Text("• The name of an existing Timer or Chain, to reuse it")
                Text("• repeat(N, step) — repeat a step N times")
                Text("• range(start, stop, step) or arange(...) — a numpy-style count sequence")
                Text(
                    "Example: repeat(4, (Work, Break)) runs Work then Break, four times in a row.",
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}

@Preview(showBackground = true, name = "New chain")
@Composable
private fun BlockConfigScreenChainPreview() {
    ClockrTheme { BlockConfigScreen(NEW_CHAIN_ROUTE_ID) }
}

@Preview(showBackground = true, name = "New timer")
@Composable
private fun BlockConfigScreenTimerPreview() {
    ClockrTheme { BlockConfigScreen(NEW_TIMER_ROUTE_ID) }
}
