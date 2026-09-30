package com.relay.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.relay.app.R

/**
 * Preset chips plus a "Custom" chip for any whole number in [range]. When [value] isn't a
 * preset, the custom chip is the selected one and shows it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NumberOptionChips(
    title: String,
    presets: List<Int>,
    value: Int,
    range: IntRange,
    label: @Composable (Int) -> String,
    onChange: (Int) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                FilterChip(selected = value == preset, onClick = { onChange(preset) }, label = { Text(label(preset)) })
            }
            val custom = value !in presets
            FilterChip(
                selected = custom,
                onClick = { editing = true },
                label = { Text(if (custom) label(value) else stringResource(R.string.option_custom)) },
            )
        }
    }
    if (editing) {
        NumberDialog(title, value, range, onDismiss = { editing = false }) { onChange(it); editing = false }
    }
}

/** Enter a whole number in [range]. */
@Composable
fun NumberDialog(title: String, initial: Int, range: IntRange, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    val parsed = text.toIntOrNull()?.takeIf { it in range }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(4) },
                singleLine = true,
                isError = parsed == null,
                supportingText = { Text(stringResource(R.string.number_range, range.first, range.last)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(enabled = parsed != null, onClick = { parsed?.let(onConfirm) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
