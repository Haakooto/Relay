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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clockr.app.R
import com.clockr.app.data.AlarmExceptionEntity
import com.clockr.app.data.OneOffAfterFire
import com.clockr.app.domain.BlockKind
import com.clockr.app.ui.common.SoundPickerRow
import com.clockr.app.ui.alarms.AlarmConfigViewModel
import com.clockr.app.ui.alarms.AlarmConfigViewModelFactory
import com.clockr.app.ui.alarms.DeleteAfterMode
import com.clockr.app.ui.alarms.NEW_ALARM_ROUTE_ID
import com.clockr.app.ui.alarms.OtherAlarmOption
import com.clockr.app.ui.theme.ClockrTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

@Composable
fun AlarmConfigScreen(alarmId: String, onBack: () -> Unit = {}) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: AlarmConfigViewModel = viewModel(factory = AlarmConfigViewModelFactory(application, alarmId))
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.alarm_config_title_new else R.string.alarm_config_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { viewModel.delete(onBack) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.alarm_delete_desc))
                        }
                    }
                    TextButton(onClick = { viewModel.save(onBack) }) { Text(stringResource(R.string.action_save)) }
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
                        label = { Text(stringResource(R.string.alarm_label_field)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item { Text(stringResource(R.string.alarm_repeat_title), style = MaterialTheme.typography.bodyLarge) }
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
                    item { Text(stringResource(R.string.alarm_after_firing_title), style = MaterialTheme.typography.bodyLarge) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.oneOffAfterFire == OneOffAfterFire.DELETE,
                                onClick = { viewModel.updateOneOffAfterFire(OneOffAfterFire.DELETE) },
                                label = { Text(stringResource(R.string.action_delete)) },
                            )
                            FilterChip(
                                selected = state.oneOffAfterFire == OneOffAfterFire.MARK_DONE,
                                onClick = { viewModel.updateOneOffAfterFire(OneOffAfterFire.MARK_DONE) },
                                label = { Text(stringResource(R.string.alarm_after_fire_mark_done)) },
                            )
                        }
                    }
                } else {
                    item { Text(stringResource(R.string.alarm_delete_after_title), style = MaterialTheme.typography.bodyLarge) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.deleteAfterMode == DeleteAfterMode.NONE,
                                onClick = { viewModel.updateDeleteAfterMode(DeleteAfterMode.NONE) },
                                label = { Text(stringResource(R.string.alarm_delete_after_never)) },
                            )
                            FilterChip(
                                selected = state.deleteAfterMode == DeleteAfterMode.COUNT,
                                onClick = { viewModel.updateDeleteAfterMode(DeleteAfterMode.COUNT) },
                                label = { Text(stringResource(R.string.alarm_delete_after_count)) },
                            )
                            FilterChip(
                                selected = state.deleteAfterMode == DeleteAfterMode.DATE,
                                onClick = { viewModel.updateDeleteAfterMode(DeleteAfterMode.DATE) },
                                label = { Text(stringResource(R.string.alarm_delete_after_date)) },
                            )
                        }
                    }
                    when (state.deleteAfterMode) {
                        DeleteAfterMode.COUNT -> item {
                            OutlinedTextField(
                                value = state.repeatCountText,
                                onValueChange = viewModel::updateRepeatCountText,
                                label = { Text(stringResource(R.string.alarm_repeat_count_label)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        DeleteAfterMode.DATE -> item {
                            var showDatePicker by remember { mutableStateOf(false) }
                            val dateLabel = state.repeatUntilEpochDay
                                ?.let { java.time.LocalDate.ofEpochDay(it).toString() }
                                ?: stringResource(R.string.alarm_pick_date)
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
                                        }) { Text(stringResource(R.string.action_ok)) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
                                    },
                                ) {
                                    DatePicker(state = datePickerState)
                                }
                            }
                        }
                        DeleteAfterMode.NONE -> {}
                    }
                }

                if (!state.isNew) {
                    item { Text(stringResource(R.string.alarm_exceptions_title), style = MaterialTheme.typography.bodyLarge) }
                    item {
                        var showAddExceptionDialog by remember { mutableStateOf(false) }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.exceptions.isEmpty()) {
                                Text(
                                    stringResource(R.string.alarm_exceptions_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            state.exceptions.forEach { exception ->
                                ExceptionRow(
                                    exception = exception,
                                    onDelete = { viewModel.deleteException(exception) },
                                )
                            }
                            OutlinedButton(onClick = { showAddExceptionDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.alarm_exception_add_button))
                            }
                        }
                        if (showAddExceptionDialog) {
                            AddExceptionDialog(
                                alarmId = state.id,
                                is24Hour = state.is24Hour,
                                otherAlarms = state.otherAlarms,
                                onDismiss = { showAddExceptionDialog = false },
                                onConfirm = { exception, alsoApplyToAlarmIds ->
                                    viewModel.addException(exception, alsoApplyToAlarmIds)
                                    showAddExceptionDialog = false
                                },
                            )
                        }
                    }
                }

                item { HorizontalDivider() }

                item { Text(stringResource(R.string.alarm_fires_title), style = MaterialTheme.typography.bodyLarge) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !state.firesBlockTarget,
                            onClick = viewModel::clearTarget,
                            label = { Text(stringResource(R.string.alarm_fires_alarm_chip)) },
                        )
                        FilterChip(
                            selected = state.firesBlockTarget,
                            onClick = viewModel::enableBlockTarget,
                            enabled = state.availableTargets.isNotEmpty(),
                            label = { Text(stringResource(R.string.alarm_fires_target_chip)) },
                        )
                    }
                }

                if (state.firesBlockTarget) {
                    item {
                        var expanded by remember { mutableStateOf(false) }
                        val current = state.availableTargets.firstOrNull { it.id == state.targetBlockId }
                        Box {
                            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(current?.label ?: stringResource(R.string.alarm_select_target))
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                state.availableTargets.forEach { option ->
                                    val kindLabel = stringResource(if (option.kind == BlockKind.CHAIN) R.string.block_kind_chain else R.string.block_kind_timer)
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.alarm_target_option_format, option.label, kindLabel)) },
                                        onClick = { viewModel.selectTarget(option); expanded = false },
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.alarm_target_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    item {
                        SoundPickerRow(stringResource(R.string.alarm_sound_label), state.soundProfileId, viewModel::updateSoundProfileId)
                    }
                    item {
                        OutlinedTextField(
                            value = state.snoozeMinutesText,
                            onValueChange = viewModel::updateSnoozeMinutesText,
                            label = { Text(stringResource(R.string.alarm_snooze_minutes_label)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = state.maxSnoozesText,
                            onValueChange = viewModel::updateMaxSnoozesText,
                            label = { Text(stringResource(R.string.alarm_max_snoozes_label)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExceptionRow(exception: AlarmExceptionEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(formatExceptionSummary(exception))
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.alarm_exception_delete_desc))
        }
    }
}

@Composable
private fun formatExceptionSummary(exception: AlarmExceptionEntity): String {
    val start = LocalDate.ofEpochDay(exception.startEpochDay).toString()
    val end = LocalDate.ofEpochDay(exception.endEpochDay).toString()
    val dateRange = if (exception.startEpochDay == exception.endEpochDay) start else "$start – $end"
    val detail = if (exception.isOverride) {
        "%02d:%02d".format(exception.overrideHour, exception.overrideMinute)
    } else {
        stringResource(R.string.alarm_exception_skip_label)
    }
    return "$dateRange – $detail"
}

@Composable
private fun AddExceptionDialog(
    alarmId: String,
    is24Hour: Boolean,
    otherAlarms: List<OtherAlarmOption>,
    onDismiss: () -> Unit,
    onConfirm: (AlarmExceptionEntity, Set<String>) -> Unit,
) {
    var startEpochDay by remember { mutableStateOf<Long?>(null) }
    var endEpochDay by remember { mutableStateOf<Long?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var isOverride by remember { mutableStateOf(false) }
    var selectedOtherIds by remember { mutableStateOf(emptySet<String>()) }

    val timePickerState = rememberTimePickerState(is24Hour = is24Hour)

    val effectiveEnd = endEpochDay ?: startEpochDay
    val isValid = startEpochDay != null && effectiveEnd != null && effectiveEnd >= startEpochDay!!

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alarm_exception_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.alarm_exception_start_date_label), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { showStartDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(startEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: stringResource(R.string.alarm_exception_pick_start_date))
                }
                Text(stringResource(R.string.alarm_exception_end_date_label), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { showEndDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(effectiveEnd?.let { LocalDate.ofEpochDay(it).toString() } ?: stringResource(R.string.alarm_exception_pick_end_date))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isOverride,
                        onClick = { isOverride = false },
                        label = { Text(stringResource(R.string.alarm_exception_skip_chip)) },
                    )
                    FilterChip(
                        selected = isOverride,
                        onClick = { isOverride = true },
                        label = { Text(stringResource(R.string.alarm_exception_override_chip)) },
                    )
                }
                if (isOverride) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TimePicker(state = timePickerState)
                    }
                }

                if (otherAlarms.isNotEmpty()) {
                    Text(stringResource(R.string.alarm_exception_apply_to_others_title), style = MaterialTheme.typography.bodySmall)
                    otherAlarms.forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = option.id in selectedOtherIds,
                                onCheckedChange = { checked ->
                                    selectedOtherIds = if (checked) selectedOtherIds + option.id else selectedOtherIds - option.id
                                },
                            )
                            Text(option.label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    val exception = AlarmExceptionEntity(
                        id = UUID.randomUUID().toString(),
                        alarmId = alarmId,
                        startEpochDay = startEpochDay!!,
                        endEpochDay = effectiveEnd!!,
                        overrideHour = if (isOverride) timePickerState.hour else null,
                        overrideMinute = if (isOverride) timePickerState.minute else null,
                    )
                    onConfirm(exception, selectedOtherIds)
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startEpochDay?.let { it * 86_400_000L })
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        startEpochDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showStartDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = effectiveEnd?.let { it * 86_400_000L })
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        endEpochDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showEndDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Preview(showBackground = true, name = "New alarm")
@Composable
private fun AlarmConfigScreenPreview() {
    ClockrTheme { AlarmConfigScreen(NEW_ALARM_ROUTE_ID) }
}
