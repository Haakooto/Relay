package com.relay.app.ui.calendar

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.relay.app.R
import com.relay.app.data.event.EventKind
import com.relay.app.ui.alarms.AlarmTargetSection
import com.relay.app.ui.alarms.DatePickerDialogFor
import com.relay.app.ui.alarms.TargetDraft
import com.relay.app.ui.alarms.TimePickerDialog
import com.relay.app.ui.common.OptionChips
import com.relay.app.ui.common.rememberDiscardGuard
import com.relay.app.util.MinuteOfDay
import com.relay.app.util.RelativeText
import java.time.LocalDate
import java.time.LocalTime

/** Editor for a calendar event or reminder (spec §10b). Modal: leaving means Save or Discard. */
@Composable
fun EventEditScreen(eventId: String?, kind: EventKind, day: LocalDate?, onDone: () -> Unit) {
    val context = LocalContext.current
    val viewModel: EventEditViewModel = viewModel(
        key = "event-edit-${eventId ?: "new"}",
        factory = EventEditViewModel.factory(context.applicationContext as Application, eventId, kind, day),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fires by viewModel.fires.state.collectAsStateWithLifecycle()
    if (!state.loaded) return
    val close = rememberDiscardGuard(viewModel::isDirty, onDone)
    var picking by remember { mutableStateOf(Picking.NONE) }
    var confirmingDelete by remember { mutableStateOf(false) }

    // Spec §13: ask for notifications when saving something that reminds, then save regardless.
    val save: () -> Unit = {
        viewModel.save { next, reminds ->
            Toast.makeText(context, savedText(context, next, reminds), Toast.LENGTH_SHORT).show()
            onDone()
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { save() }
    val onSave: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted || state.lead == null) save() else notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    EventPickers(picking, state, viewModel) { picking = Picking.NONE }
    if (confirmingDelete) DeleteDialog(state, onDismiss = { confirmingDelete = false }) { viewModel.delete(onDone) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EditorTitle(state, canSave = viewModel.canSave(state), onClose = close, onSave = onSave)
        OutlinedTextField(
            value = state.title,
            onValueChange = { v -> viewModel.update { it.copy(title = v) } },
            label = { Text(stringResource(R.string.event_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        EventWhenSection(state, viewModel) { picking = it }
        EventRepeatSection(state, viewModel) { picking = it }
        OutlinedTextField(
            value = state.note,
            onValueChange = { v -> viewModel.update { it.copy(note = v) } },
            label = { Text(stringResource(R.string.event_note)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()
        ReminderSection(state, fires, viewModel)

        if (!state.isNew) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirmingDelete = true }) { Text(stringResource(R.string.action_delete)) }
        }
    }
}

/** When to remind (events pick; a reminder is at its time) and what it does then. */
@Composable
private fun ReminderSection(state: EventEditState, fires: TargetDraft, viewModel: EventEditViewModel) {
    Text(stringResource(R.string.calendar_kind_reminder), style = MaterialTheme.typography.titleLarge)
    if (state.kind == EventKind.EVENT) {
        OptionChips(
            title = stringResource(R.string.event_remind_when),
            options = listOf<Int?>(null) + state.leads,
            selected = state.lead,
            label = { lead -> lead?.let { CalendarText.lead(it, state.allDay, state.is24h) } ?: stringResource(R.string.lead_none) },
            onSelect = { v -> viewModel.update { it.copy(lead = v) } },
        )
    }
    if (state.lead != null) AlarmTargetSection(fires, viewModel.fires)
}

/** The toast after saving: when the reminder comes, that it has passed, or just "Saved". */
private fun savedText(context: Context, next: Long?, reminds: Boolean): String = when {
    !reminds -> context.getString(R.string.event_saved)
    next == null -> context.getString(R.string.event_reminder_passed)
    else -> context.getString(R.string.event_reminder_in, RelativeText.until(context, next - System.currentTimeMillis()))
}

@Composable
private fun EditorTitle(state: EventEditState, canSave: Boolean, onClose: () -> Unit, onSave: () -> Unit) {
    val isEvent = state.kind == EventKind.EVENT
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel)) }
        Text(
            stringResource(
                when {
                    isEvent && state.isNew -> R.string.calendar_new_event
                    isEvent -> R.string.event_edit
                    state.isNew -> R.string.calendar_new_reminder
                    else -> R.string.reminder_edit
                },
            ),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onSave, enabled = canSave) { Text(stringResource(R.string.action_save)) }
    }
}

@Composable
private fun DeleteDialog(state: EventEditState, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val isEvent = state.kind == EventKind.EVENT
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isEvent) R.string.event_delete_title else R.string.reminder_delete_title)) },
        text = { if (isEvent && state.lead != null) Text(stringResource(R.string.event_delete_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The open date or time picker, if any. Each writes its field and closes. */
@Composable
private fun EventPickers(picking: Picking, state: EventEditState, viewModel: EventEditViewModel, onClose: () -> Unit) {
    val set: (EventEditState.() -> EventEditState) -> Unit = { change ->
        viewModel.update { it.change() }
        onClose()
    }
    when (picking) {
        Picking.NONE -> {}
        Picking.DATE -> DatePickerDialogFor(state.date, onDismiss = onClose) { d -> set { copy(date = d) } }
        Picking.START -> MinutePicker(state.startMinute, state.is24h, onClose) { m -> set { copy(startMinute = m) } }
        Picking.END_TIME -> {
            val end = state.endMinute ?: (state.startMinute + DEFAULT_LENGTH).coerceAtMost(LAST_MINUTE)
            MinutePicker(end, state.is24h, onClose) { m -> set { copy(endMinute = m) } }
        }
        Picking.END_DATE -> DatePickerDialogFor(state.endDate ?: state.date, onDismiss = onClose) { d -> set { copy(endDate = d) } }
        Picking.UNTIL -> DatePickerDialogFor(state.until ?: state.date, onDismiss = onClose) { d -> set { copy(until = d) } }
    }
}

/** The time picker, speaking minutes after midnight. */
@Composable
private fun MinutePicker(minute: Int, is24h: Boolean, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val time = MinuteOfDay.toTime(minute)
    TimePickerDialog(time.hour, time.minute, is24h, onDismiss = onDismiss) { h, m -> onPick(MinuteOfDay.of(LocalTime.of(h, m))) }
}

/** A new end time defaults to an hour after the start. */
private const val DEFAULT_LENGTH = 60
private const val LAST_MINUTE = 23 * 60 + 59
