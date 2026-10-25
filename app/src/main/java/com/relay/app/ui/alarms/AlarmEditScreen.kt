package com.relay.app.ui.alarms

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.relay.app.R
import com.relay.app.data.schedule.AfterFire
import com.relay.app.data.schedule.ExceptionKind
import com.relay.app.data.schedule.TargetKind
import com.relay.app.ui.calendar.CalendarPicker
import com.relay.app.ui.common.NumberOptionChips
import com.relay.app.ui.common.OptionChips
import com.relay.app.ui.common.SoundPickerRow
import com.relay.app.ui.common.rememberDiscardGuard
import com.relay.app.util.AppLocale
import com.relay.app.util.RelativeText
import com.relay.app.util.TimeText
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmEditScreen(scheduleId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val viewModel: AlarmEditViewModel = viewModel(
        key = "alarm-edit-${scheduleId ?: "new"}",
        factory = AlarmEditViewModel.factory(context.applicationContext as Application, scheduleId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fires by viewModel.fires.state.collectAsStateWithLifecycle()
    if (!state.loaded) return
    val close = rememberDiscardGuard(viewModel::isDirty, onDone)

    // Stock behaviour: a new alarm opens straight into the time picker.
    var pickingTime by rememberSaveable { mutableStateOf(state.isNew) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingEndDate by remember { mutableStateOf(false) }
    var addingException by remember { mutableStateOf(false) }

    // Spec §13: ask for notifications at the first alarm, then save regardless of the answer.
    val save: () -> Unit = { viewModel.save { next -> armedToast(context, next); onDone() } }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { save() }
    val onSave: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) save()
        else notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    if (pickingTime) {
        TimePickerDialog(state.hour, state.minute, state.is24h, onDismiss = {
            pickingTime = false
        }) { h, m ->
            viewModel.update { it.copy(hour = h, minute = m) }
            pickingTime = false
        }
    }
    if (pickingDate) {
        DatePickerDialogFor(state.oneOffDate, onDismiss = { pickingDate = false }) { date ->
            viewModel.update { it.copy(oneOffDate = date) }
            pickingDate = false
        }
    }
    if (pickingEndDate) {
        DatePickerDialogFor(state.endDate, onDismiss = { pickingEndDate = false }) { date ->
            viewModel.update { it.copy(endDate = date, endRule = EndRule.DATE) }
            pickingEndDate = false
        }
    }
    if (addingException) {
        ExceptionDialog(
            defaultHour = state.hour, defaultMinute = state.minute, is24h = state.is24h, others = state.others,
            onDismiss = { addingException = false },
        ) { start, end, kind, h, m, alsoFor ->
            viewModel.addException(start, end, kind, h, m, alsoFor)
            addingException = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel)) }
            Text(
                stringResource(if (state.isNew) R.string.alarm_new else R.string.alarm_edit),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        // Save sits next to the time, so it's reachable without scrolling the long option list.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                TimeText.of(LocalTime.of(state.hour, state.minute), state.is24h),
                fontSize = 64.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).clickable { pickingTime = true },
            )
            Button(onClick = onSave, enabled = viewModel.fires.canSave(fires)) { Text(stringResource(R.string.action_save)) }
        }
        OutlinedTextField(
            value = state.label,
            onValueChange = { v -> viewModel.update { it.copy(label = v) } },
            label = { Text(stringResource(R.string.alarm_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        CalendarPicker(state.calendars, state.calendarId) { id -> viewModel.update { it.copy(calendarId = id) } }

        Text(stringResource(R.string.alarm_repeat), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RelativeText.weekOrder(state.firstDay).forEach { day ->
                val on = day in state.repeatDays
                FilterChip(
                    selected = on,
                    onClick = { viewModel.update { it.copy(repeatDays = if (on) it.repeatDays - day else it.repeatDays + day) } },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, AppLocale.formatting)) },
                )
            }
        }

        if (state.repeatDays.isEmpty()) {
            val date = state.oneOffDate
            OutlinedButton(onClick = { pickingDate = true }) {
                Text(date?.let { RelativeText.date(context, it) } ?: stringResource(R.string.alarm_next_occurrence))
            }
            OptionChips(
                title = stringResource(R.string.alarm_after_ringing),
                options = AfterFire.entries,
                selected = state.afterFire,
                label = { stringResource(if (it == AfterFire.DELETE) R.string.alarm_after_delete else R.string.alarm_after_disable) },
                onSelect = { v -> viewModel.update { it.copy(afterFire = v) } },
            )
        } else {
            OptionChips(
                title = stringResource(R.string.alarm_ends),
                options = EndRule.entries,
                selected = state.endRule,
                label = {
                    when (it) {
                        EndRule.NEVER -> stringResource(R.string.alarm_ends_never)
                        EndRule.DATE -> state.endDate?.let { d -> stringResource(R.string.alarm_ends_on, RelativeText.date(context, d)) }
                            ?: stringResource(R.string.alarm_ends_date)
                        EndRule.COUNT -> stringResource(R.string.alarm_ends_count)
                    }
                },
                onSelect = { v ->
                    if (v == EndRule.DATE) pickingEndDate = true
                    else viewModel.update { it.copy(endRule = v) }
                },
            )
            if (state.endRule == EndRule.COUNT) {
                OutlinedTextField(
                    value = state.endCount.toString(),
                    onValueChange = { v -> v.toIntOrNull()?.let { n -> viewModel.update { it.copy(endCount = n.coerceIn(1, 999)) } } },
                    label = { Text(stringResource(R.string.alarm_ends_count_field)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        }

        HorizontalDivider()
        AlarmTargetSection(fires, viewModel.fires)

        // Sound and snooze only matter when it rings.
        if (fires.target == TargetKind.RING) {
        HorizontalDivider()
        SoundPickerRow(stringResource(R.string.alarm_sound), state.soundUri) { uri -> viewModel.update { it.copy(soundUri = uri) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.alarm_vibrate), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = state.vibrate, onCheckedChange = { v -> viewModel.update { it.copy(vibrate = v) } })
        }
        OptionChips(
            title = stringResource(R.string.alarm_ramp),
            options = RAMP_OPTIONS, selected = state.rampSeconds,
            label = { if (it == 0) stringResource(R.string.option_off) else stringResource(R.string.seconds_short, it) },
            onSelect = { v -> viewModel.update { it.copy(rampSeconds = v) } },
        )
        OptionChips(
            title = stringResource(R.string.alarm_max_snoozes),
            options = MAX_SNOOZE_OPTIONS, selected = state.maxSnoozes,
            label = {
                when (it) {
                    null -> stringResource(R.string.option_unlimited)
                    0 -> stringResource(R.string.option_none)
                    else -> it.toString()
                }
            },
            onSelect = { v -> viewModel.update { it.copy(maxSnoozes = v) } },
        )
        // No snoozes allowed: the length is irrelevant, so don't ask for it.
        if (state.maxSnoozes != 0) {
            NumberOptionChips(
                title = stringResource(R.string.alarm_snooze_length),
                presets = SNOOZE_PRESETS, value = state.snoozeMinutes, range = SNOOZE_RANGE,
                label = { stringResource(R.string.minutes_short, it) },
                onChange = { v -> viewModel.update { it.copy(snoozeMinutes = v) } },
            )
        }
        }

        HorizontalDivider()
        Text(stringResource(R.string.exceptions_title), style = MaterialTheme.typography.titleLarge)
        if (state.isNew) {
            Text(
                stringResource(R.string.exceptions_save_first),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        } else {
            state.exceptions.forEach { ex ->
                val start = LocalDate.ofEpochDay(ex.startEpochDay)
                val end = LocalDate.ofEpochDay(ex.endEpochDay)
                val dates = if (start == end) RelativeText.date(context, start)
                else stringResource(R.string.exception_range, RelativeText.date(context, start), RelativeText.date(context, end))
                val what = if (ex.kind == ExceptionKind.SKIP) stringResource(R.string.exception_skip)
                else stringResource(R.string.exception_at, TimeText.of(LocalTime.of(ex.retimeHour ?: 0, ex.retimeMinute ?: 0), state.is24h))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$what · $dates", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.deleteException(ex) }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_delete))
                    }
                }
            }
            OutlinedButton(onClick = { addingException = true }) { Text(stringResource(R.string.exception_add)) }
        }

        if (!state.isNew) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { viewModel.delete(onDone) }) { Text(stringResource(R.string.action_delete)) }
        }
    }
}

private val RAMP_OPTIONS = listOf(0, 15, 30, 60)
val SNOOZE_PRESETS = listOf(5, 10, 15, 20, 30)
val SNOOZE_RANGE = 1..240
/** 0 = no snoozing at all ("None"), null = unlimited. */
private val MAX_SNOOZE_OPTIONS = listOf<Int?>(0, 1, 3, 5, null)
