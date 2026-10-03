package com.relay.app.ui.timers

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.relay.app.R
import com.relay.app.ui.common.SoundPickerRow
import com.relay.app.ui.common.rememberDiscardGuard
import com.relay.app.util.DurationText
import com.relay.chainlang.ChainLang

@Composable
fun TimerEditScreen(timerId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val viewModel: TimerEditViewModel = viewModel(
        key = "timer-edit-${timerId ?: "new"}",
        factory = TimerEditViewModel.factory(context.applicationContext as Application, timerId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return
    val close = rememberDiscardGuard(viewModel::isDirty, onDone)

    state.deleteReferences?.let { refs ->
        DeleteDialog(state.label, refs, onDismiss = viewModel::cancelDelete) { viewModel.delete(onDone) }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = close) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel)) }
            Text(
                stringResource(if (state.isNew) R.string.timer_new else R.string.timer_edit),
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { viewModel.start(onDone) }, enabled = state.seconds != null) { Text(stringResource(R.string.action_start)) }
            Button(onClick = { viewModel.save(onDone) }, enabled = state.seconds != null && ChainLang.isReferenceableLabel(state.label.trim())) {
                Text(stringResource(R.string.action_save))
            }
        }
        OutlinedTextField(
            value = state.label,
            onValueChange = viewModel::setLabel,
            label = { Text(stringResource(R.string.block_label)) },
            isError = state.label.isNotBlank() && !ChainLang.isReferenceableLabel(state.label.trim()),
            supportingText = {
                Text(stringResource(if (state.label.isNotBlank() && !ChainLang.isReferenceableLabel(state.label.trim())) R.string.save_label_invalid else R.string.block_label_hint))
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.durationExpr,
            onValueChange = viewModel::setDuration,
            label = { Text(stringResource(R.string.timer_duration)) },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace),
            isError = state.durationError != null || state.notSingle,
            supportingText = {
                Text(
                    when {
                        state.durationError != null -> state.durationError!!
                        state.notSingle -> stringResource(R.string.timer_one_duration)
                        else -> state.seconds?.let { stringResource(R.string.timer_duration_is, DurationText.short(it)) }.orEmpty()
                    },
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SoundPickerRow(stringResource(R.string.alarm_sound), state.sound.uri) { viewModel.setSound(state.sound.copy(uri = it)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.alarm_vibrate), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = state.sound.vibrate, onCheckedChange = { viewModel.setSound(state.sound.copy(vibrate = it)) })
        }
        state.saveError?.let { Text(saveErrorText(it), color = MaterialTheme.colorScheme.error) }
        if (!state.isNew) TextButton(onClick = viewModel::askDelete) { Text(stringResource(R.string.action_delete)) }
    }
}

/** Deleting a block other relays use: say which ones will break. */
@Composable
fun DeleteDialog(label: String, references: List<String>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_title, label)) },
        text = {
            if (references.isNotEmpty()) Text(stringResource(R.string.delete_breaks, references.joinToString(", ")))
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
