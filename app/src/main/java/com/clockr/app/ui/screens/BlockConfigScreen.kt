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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clockr.app.R
import com.clockr.app.data.TransitionBeepTone
import com.clockr.app.domain.BlockKind
import com.clockr.app.domain.TransitionMode
import com.clockr.app.run.TransitionBeep
import com.clockr.app.ui.common.SoundPickerRow
import com.clockr.app.ui.common.displayName
import com.clockr.app.ui.theme.ClockrTheme
import com.clockr.app.ui.timers.BlockConfigViewModel
import com.clockr.app.ui.timers.BlockConfigViewModelFactory
import com.clockr.app.ui.timers.NEW_CHAIN_ROUTE_ID
import com.clockr.app.ui.timers.NEW_TIMER_ROUTE_ID
import com.clockr.app.ui.timers.ResolvedStepUi
import com.clockr.app.util.formatDuration
import kotlinx.coroutines.launch

@Composable
fun BlockConfigScreen(blockId: String, onBack: () -> Unit = {}) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: BlockConfigViewModel = viewModel(
        factory = BlockConfigViewModelFactory(application, blockId),
    )
    val state by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.label.ifBlank { stringResource(R.string.block_config_untitled) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { viewModel.requestDelete(onBack) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                    TextButton(
                        enabled = state.error == null && state.labelError == null && !state.loading,
                        onClick = { viewModel.save(onBack) },
                    ) {
                        Text(stringResource(R.string.action_save))
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
                Text(stringResource(R.string.common_not_found), modifier = Modifier.align(Alignment.Center))
            }
            else -> LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = state.label,
                        onValueChange = viewModel::updateLabel,
                        label = { Text(stringResource(if (state.persistent) R.string.block_config_label_required else R.string.block_config_label_optional)) },
                        supportingText = {
                            Text(
                                state.labelError
                                    ?: stringResource(if (state.persistent) R.string.block_config_label_hint_required else R.string.block_config_label_hint_optional),
                            )
                        },
                        isError = state.labelError != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (state.kind == BlockKind.TIMER) {
                    item {
                        OutlinedTextField(
                            value = state.durationExpr,
                            onValueChange = viewModel::updateDurationExpr,
                            label = { Text(stringResource(R.string.block_config_duration_label)) },
                            supportingText = {
                                Text(
                                    state.error
                                        ?: state.resolvedSeconds?.let { stringResource(R.string.block_config_duration_resolved, formatDuration(it)) }
                                        ?: "",
                                )
                            },
                            isError = state.error != null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.block_config_persistent_label), style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = state.persistent, onCheckedChange = viewModel::updatePersistent)
                    }
                }

                item {
                    SoundPickerRow(
                        label = stringResource(if (state.kind == BlockKind.CHAIN) R.string.block_config_sound_chain else R.string.block_config_sound_timer),
                        soundProfileId = state.soundProfileId,
                        onPicked = viewModel::updateSoundProfileId,
                    )
                }

                if (state.kind == BlockKind.CHAIN) {
                    item { Text(stringResource(R.string.block_config_transition_mode_title), style = MaterialTheme.typography.bodyLarge) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.transitionMode == TransitionMode.REQUIRE_INTERACTION,
                                onClick = { viewModel.updateTransitionMode(TransitionMode.REQUIRE_INTERACTION) },
                                label = { Text(stringResource(R.string.block_config_transition_require_interaction)) },
                            )
                            FilterChip(
                                selected = state.transitionMode == TransitionMode.SEAMLESS,
                                onClick = { viewModel.updateTransitionMode(TransitionMode.SEAMLESS) },
                                label = { Text(stringResource(R.string.block_config_transition_seamless)) },
                            )
                        }
                    }

                    if (state.transitionMode == TransitionMode.SEAMLESS) {
                        item { Text(stringResource(R.string.block_config_beep_title), style = MaterialTheme.typography.bodyLarge) }
                        item {
                            Text(
                                stringResource(R.string.block_config_beep_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TransitionBeepTone.entries.forEach { option ->
                                    FilterChip(
                                        selected = state.transitionBeepTone == option,
                                        onClick = {
                                            coroutineScope.launch { TransitionBeep.play(option) }
                                            viewModel.updateTransitionBeepTone(option)
                                        },
                                        label = { Text(option.displayName()) },
                                    )
                                }
                            }
                        }
                    }

                    item { HorizontalDivider() }
                    item {
                        var showHelp by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.block_config_content_title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 4.dp))
                            IconButton(onClick = { showHelp = true }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = stringResource(R.string.block_config_syntax_help_desc))
                            }
                        }
                        OutlinedTextField(
                            value = state.contentExpr,
                            onValueChange = viewModel::updateContentExpr,
                            supportingText = { Text(state.error ?: stringResource(R.string.block_config_content_hint)) },
                            isError = state.error != null,
                            minLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (showHelp) {
                            ChainSyntaxHelpDialog(onDismiss = { showHelp = false })
                        }
                    }
                    if (state.error == null) {
                        item { Text(stringResource(R.string.block_config_preview_title), style = MaterialTheme.typography.titleLarge) }
                        items(state.resolvedSteps) { step -> ResolvedStepRow(step) }
                    }
                }
            }
        }
    }

    state.pendingDeleteUsedBy?.let { usedBy ->
        val refWord = stringResource(if (usedBy.size > 1) R.string.block_config_ref_plural else R.string.block_config_ref_singular)
        AlertDialog(
            onDismissRequest = viewModel::cancelPendingDelete,
            title = { Text(stringResource(R.string.block_config_still_in_use_title)) },
            text = { Text(stringResource(R.string.block_config_still_in_use_message, usedBy.joinToString(", "), refWord)) },
            confirmButton = { TextButton(onClick = { viewModel.confirmDelete(onBack) }) { Text(stringResource(R.string.block_config_delete_anyway)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelPendingDelete) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ResolvedStepRow(step: ResolvedStepUi) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(step.label ?: stringResource(R.string.block_config_step_placeholder), style = MaterialTheme.typography.bodyLarge)
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
        title = { Text(stringResource(R.string.chain_help_title)) },
        text = {
            Column {
                Text(stringResource(R.string.chain_help_intro))
                Text(
                    stringResource(R.string.chain_help_example_syntax),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                Text(stringResource(R.string.chain_help_step_types))
                Text(stringResource(R.string.chain_help_step_duration), modifier = Modifier.padding(top = 4.dp))
                Text(stringResource(R.string.chain_help_step_reference))
                Text(stringResource(R.string.chain_help_step_repeat))
                Text(stringResource(R.string.chain_help_step_range))
                Text(
                    stringResource(R.string.chain_help_example),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.chain_help_got_it)) } },
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
