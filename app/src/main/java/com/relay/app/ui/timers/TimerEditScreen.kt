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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    val focusManager = LocalFocusManager.current
    val durationFocus = remember { FocusRequester() }
    // The keypad types only digits and units; an existing "RANDOM(5, 10)" opens on the keyboard.
    var keypadMode by remember { mutableStateOf(state.durationExpr.isEmpty() || DurationText.isPlain(state.durationExpr)) }
    var showKeypad by remember { mutableStateOf(state.isNew) }
    // Set by the keyboard icon only: focus once the field is editable, so the keyboard comes up.
    var wantKeyboard by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(keypadMode, wantKeyboard) {
        if (!keypadMode && wantKeyboard) {
            durationFocus.requestFocus()
            keyboard?.show()
            wantKeyboard = false
        }
    }

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
        // Length first, on the app's own keypad (open from the start for a new timer). The
        // keyboard icon swaps to the full keyboard for anything else the relay language says.
        OutlinedTextField(
            value = state.durationExpr,
            onValueChange = viewModel::setDuration,
            label = { Text(stringResource(R.string.timer_duration)) },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace),
            readOnly = keypadMode,
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
            trailingIcon = {
                IconButton(onClick = {
                    keypadMode = !keypadMode
                    if (keypadMode) { focusManager.clearFocus(); showKeypad = true } else { showKeypad = false; wantKeyboard = true }
                }) {
                    Icon(
                        if (keypadMode) Icons.Outlined.Keyboard else Icons.Outlined.Dialpad,
                        contentDescription = stringResource(if (keypadMode) R.string.keypad_use_keyboard else R.string.keypad_use_keypad),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(durationFocus)
                .onFocusChanged { if (it.isFocused && keypadMode) showKeypad = true },
        )
        if (keypadMode && showKeypad) {
            DurationKeypad(
                onType = { focusManager.clearFocus(); viewModel.setDuration(state.durationExpr + it) },
                onBackspace = { viewModel.setDuration(state.durationExpr.dropLast(1)) },
                onClear = { viewModel.setDuration("") },
                onAdd = { focusManager.clearFocus(); viewModel.addToDuration(it) },
            )
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
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) showKeypad = false },
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
