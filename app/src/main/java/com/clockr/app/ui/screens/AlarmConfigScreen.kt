@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.clockr.app.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.clockr.app.data.OneOffAfterFire
import com.clockr.app.domain.BlockKind
import com.clockr.app.ui.common.SoundPickerRow
import com.clockr.app.ui.alarms.AlarmConfigViewModel
import com.clockr.app.ui.alarms.AlarmConfigViewModelFactory
import com.clockr.app.ui.alarms.DeleteAfterMode
import com.clockr.app.ui.alarms.NEW_ALARM_ROUTE_ID
import com.clockr.app.ui.theme.ClockrTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AlarmConfigScreen(alarmId: String, onBack: () -> Unit = {}) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: AlarmConfigViewModel = viewModel(factory = AlarmConfigViewModelFactory(application, alarmId))
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New alarm" else "Edit alarm") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { viewModel.delete(onBack) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete alarm")
                        }
                    }
                    TextButton(onClick = { viewModel.save(onBack) }) { Text("Save") }
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
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                state.error?.let { message ->
                    item {
                        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                item {
                    val timePickerState = rememberTimePickerState(
                        initialHour = state.hour,
                        initialMinute = state.minute,
                        is24Hour = state.is24Hour,
                    )
                    LaunchedEffect(timePickerState.hour, timePickerState.minute) {
                        viewModel.updateTime(timePickerState.hour, timePickerState.minute)
                    }
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TimePicker(state = timePickerState)
                    }
                }

                item {
                    OutlinedTextField(
                        value = state.label,
                        onValueChange = viewModel::updateLabel,
                        label = { Text("Label") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item { Text("Repeat", style = MaterialTheme.typography.bodyLarge) }
                item {
                    val orderedDays = remember(state.firstDayOfWeek) {
                        val all = DayOfWeek.entries
                        val startIndex = all.indexOf(state.firstDayOfWeek)
                        all.drop(startIndex) + all.take(startIndex)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        orderedDays.forEach { day ->
                            FilterChip(
                                selected = day in state.repeatDays,
                                onClick = { viewModel.toggleDay(day) },
                                label = { Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault())) },
                            )
                        }
                    }
                }

                if (!state.isRepeating) {
                    item { Text("After firing", style = MaterialTheme.typography.bodyLarge) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.oneOffAfterFire == OneOffAfterFire.DELETE,
                                onClick = { viewModel.updateOneOffAfterFire(OneOffAfterFire.DELETE) },
                                label = { Text("Delete") },
                            )
                            FilterChip(
                                selected = state.oneOffAfterFire == OneOffAfterFire.MARK_DONE,
                                onClick = { viewModel.updateOneOffAfterFire(OneOffAfterFire.MARK_DONE) },
                                label = { Text("Mark as done") },
                            )
                        }
                    }
                } else {
                    item { Text("Delete after", style = MaterialTheme.typography.bodyLarge) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.deleteAfterMode == DeleteAfterMode.NONE,
                                onClick = { viewModel.updateDeleteAfterMode(DeleteAfterMode.NONE) },
                                label = { Text("Never") },
                            )
                            FilterChip(
                                selected = state.deleteAfterMode == DeleteAfterMode.COUNT,
                                onClick = { viewModel.updateDeleteAfterMode(DeleteAfterMode.COUNT) },
                                label = { Text("After N times") },
                            )
                            FilterChip(
                                selected = state.deleteAfterMode == DeleteAfterMode.DATE,
                                onClick = { viewModel.updateDeleteAfterMode(DeleteAfterMode.DATE) },
                                label = { Text("Until date") },
                            )
                        }
                    }
                    when (state.deleteAfterMode) {
                        DeleteAfterMode.COUNT -> item {
                            OutlinedTextField(
                                value = state.repeatCountText,
                                onValueChange = viewModel::updateRepeatCountText,
                                label = { Text("Number of occurrences") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        DeleteAfterMode.DATE -> item {
                            var showDatePicker by remember { mutableStateOf(false) }
                            val dateLabel = state.repeatUntilEpochDay
                                ?.let { java.time.LocalDate.ofEpochDay(it).toString() }
                                ?: "Pick a date"
                            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(dateLabel)
                            }
                            if (showDatePicker) {
                                val datePickerState = rememberDatePickerState(
                                    initialSelectedDateMillis = state.repeatUntilEpochDay
                                        ?.let { it * 86_400_000L },
                                )
                                DatePickerDialog(
                                    onDismissRequest = { showDatePicker = false },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            datePickerState.selectedDateMillis?.let { millis ->
                                                val epochDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                                                viewModel.updateRepeatUntilEpochDay(epochDay)
                                            }
                                            showDatePicker = false
                                        }) { Text("OK") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                                    },
                                ) {
                                    DatePicker(state = datePickerState)
                                }
                            }
                        }
                        DeleteAfterMode.NONE -> {}
                    }
                }

                item { HorizontalDivider() }

                item { Text("Fires", style = MaterialTheme.typography.bodyLarge) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !state.firesBlockTarget,
                            onClick = viewModel::clearTarget,
                            label = { Text("Alarm") },
                        )
                        FilterChip(
                            selected = state.firesBlockTarget,
                            onClick = viewModel::enableBlockTarget,
                            enabled = state.availableTargets.isNotEmpty(),
                            label = { Text("Timer or Chain") },
                        )
                    }
                }

                if (state.firesBlockTarget) {
                    item {
                        var expanded by remember { mutableStateOf(false) }
                        val current = state.availableTargets.firstOrNull { it.id == state.targetBlockId }
                        Box {
                            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(current?.label ?: "Select a Timer or Chain")
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                state.availableTargets.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text("${option.label} (${if (option.kind == BlockKind.CHAIN) "Chain" else "Timer"})") },
                                        onClick = { viewModel.selectTarget(option); expanded = false },
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            "Uses that Timer/Chain's own sound and Stop/Restart/+1min prompt — sound and snooze below don't apply.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    item {
                        SoundPickerRow("Sound", state.soundProfileId, viewModel::updateSoundProfileId)
                    }
                    item {
                        OutlinedTextField(
                            value = state.snoozeMinutesText,
                            onValueChange = viewModel::updateSnoozeMinutesText,
                            label = { Text("Snooze length (minutes)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = state.maxSnoozesText,
                            onValueChange = viewModel::updateMaxSnoozesText,
                            label = { Text("Max snoozes (blank = unlimited)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "New alarm")
@Composable
private fun AlarmConfigScreenPreview() {
    ClockrTheme { AlarmConfigScreen(NEW_ALARM_ROUTE_ID) }
}
