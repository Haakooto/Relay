package com.relay.app.ui.stopwatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.relay.app.R
import com.relay.app.stopwatch.CorrectionKind
import com.relay.app.stopwatch.CorrectionView
import com.relay.app.stopwatch.EditError
import com.relay.app.stopwatch.LapEdits
import com.relay.app.stopwatch.LapView
import com.relay.app.util.DurationText

/** Edit a stopwatch's tag list: chips with ✕, plus a field to add one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagListEditor(tags: List<String>, onChange: (List<String>) -> Unit) {
    var newTag by remember { mutableStateOf("") }
    val add = {
        val t = newTag.trim()
        if (t.isNotEmpty() && t !in tags) onChange(tags + t)
        newTag = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.stopwatch_tags), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                InputChip(
                    selected = false, onClick = { onChange(tags - tag) }, label = { Text(tag) },
                    trailingIcon = { Icon(Icons.Outlined.Close, stringResource(R.string.action_delete)) },
                )
            }
        }
        OutlinedTextField(
            newTag, { newTag = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.stopwatch_add_tag)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            trailingIcon = { TextButton(onClick = add, enabled = newTag.isNotBlank()) { Text(stringResource(R.string.stopwatch_add_tag_button)) } },
        )
    }
}

/** Optional name, start offset (t0) and tags; starts at once. */
@Composable
fun NewStopwatchDialog(onDismiss: () -> Unit, onStart: (String, Long, List<String>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var h by remember { mutableStateOf("") }
    var m by remember { mutableStateOf("") }
    var s by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(emptyList<String>()) }
    // Below zero: counts up from e.g. -2:00 through 0 and on.
    var negative by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stopwatch_new)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.stopwatch_name)) }, singleLine = true)
                Text(stringResource(R.string.stopwatch_t0), style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = negative, onClick = { negative = !negative }, label = { Text("−") })
                    listOf(Triple(h, { v: String -> h = v }, "h"), Triple(m, { v: String -> m = v }, "m"), Triple(s, { v: String -> s = v }, "s"))
                        .forEach { (value, set, unit) ->
                            OutlinedTextField(
                                value, { set(it.filter(Char::isDigit).take(3)) }, label = { Text(unit) }, singleLine = true,
                                modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                }
                TagListEditor(tags) { tags = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t0 = ((h.toLongOrNull() ?: 0) * 3600 + (m.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)) * 1000
                onStart(name, if (negative) -t0 else t0, tags)
            }) { Text(stringResource(R.string.action_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun EditStopwatchDialog(name: String, tags: List<String>, onDismiss: () -> Unit, onSave: (String, List<String>) -> Unit) {
    var n by remember { mutableStateOf(name) }
    var t by remember { mutableStateOf(tags) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stopwatch_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(n, { n = it }, label = { Text(stringResource(R.string.stopwatch_name)) }, singleLine = true)
                TagListEditor(t) { t = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(n, t) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A lap's note and tags (from the stopwatch's own tag list). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LapDetailsDialog(lap: LapView, available: List<String>, onDismiss: () -> Unit, onSave: (String, List<String>) -> Unit) {
    var note by remember { mutableStateOf(lap.lap.note) }
    var tags by remember { mutableStateOf(lap.tags) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stopwatch_lap_title, lap.lap.number)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.stopwatch_note)) })
                if (available.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        available.forEach { tag ->
                            val on = tag in tags
                            FilterChip(selected = on, onClick = { tags = if (on) tags - tag else tags + tag }, label = { Text(tag) })
                        }
                    }
                } else {
                    Text(stringResource(R.string.stopwatch_no_tags_hint), style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(note, tags) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Remove, insert or adjust a lap boundary, with an optional label. Amounts are free text
 * ("5", "5s", "5 min", "-5s", "1:30"); a bare number is seconds here, and the reading is shown.
 */
@Composable
fun CorrectionDialog(
    kind: CorrectionKind, lap: LapView, onDismiss: () -> Unit,
    onConfirm: (amountMillis: Long, label: String, onError: (EditError) -> Unit) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<EditError?>(null) }
    val parsed = LapEdits.parseAmount(amount)
    val needsAmount = kind != CorrectionKind.REMOVE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when (kind) {
                        CorrectionKind.REMOVE -> R.string.correction_remove_title
                        CorrectionKind.INSERT -> R.string.correction_insert_title
                        CorrectionKind.ADJUST -> R.string.correction_adjust_title
                    },
                    lap.lap.number,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        when (kind) {
                            CorrectionKind.REMOVE -> if (lap.lap.number == 1) R.string.correction_remove_first_help else R.string.correction_remove_help
                            CorrectionKind.INSERT -> R.string.correction_insert_help
                            CorrectionKind.ADJUST -> R.string.correction_adjust_help
                        },
                        DurationText.stopwatch(lap.deltaMillis),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (needsAmount) {
                    OutlinedTextField(
                        amount, { amount = it; error = null }, singleLine = true,
                        label = { Text(stringResource(if (kind == CorrectionKind.INSERT) R.string.correction_into else R.string.correction_by)) },
                        isError = (amount.isNotBlank() && parsed == null) || error != null,
                        supportingText = {
                            Text(
                                when {
                                    error != null -> stringResource(errorText(error!!))
                                    amount.isBlank() -> stringResource(R.string.correction_amount_hint)
                                    parsed == null -> stringResource(R.string.correction_amount_unreadable)
                                    else -> (if (kind == CorrectionKind.ADJUST && parsed >= 0) "+" else "") + DurationText.signedStopwatch(parsed)
                                },
                            )
                        },
                    )
                } else if (error != null) {
                    Text(stringResource(errorText(error!!)), color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(label, { label = it }, singleLine = true, label = { Text(stringResource(R.string.correction_label)) })
            }
        },
        confirmButton = {
            TextButton(
                enabled = !needsAmount || parsed != null,
                onClick = { onConfirm(if (needsAmount) parsed ?: 0 else 0, label) { error = it } },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun errorText(e: EditError) = when (e) {
    EditError.NO_SUCH_LAP -> R.string.correction_error_gone
    EditError.ZERO_AMOUNT -> R.string.correction_error_zero
    EditError.OUT_OF_RANGE -> R.string.correction_error_range
}

/** The stopwatch's correction history: undone ones greyed until a new correction drops them. */
@Composable
fun CorrectionsDialog(
    corrections: List<CorrectionView>, canUndo: Boolean, canRedo: Boolean,
    onUndo: () -> Unit, onRedo: () -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.corrections_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (corrections.isEmpty()) Text(stringResource(R.string.corrections_none))
                corrections.forEach { c ->
                    val alpha = if (c.correction.undone) 0.4f else 1f
                    Column {
                        Text(
                            correctionSummary(c),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        )
                        val sub = listOfNotNull(
                            c.correction.label.ifBlank { null },
                            if (c.correction.undone) stringResource(R.string.corrections_undone) else null,
                        ).joinToString(" · ")
                        if (sub.isNotEmpty()) {
                            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f * alpha))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onUndo, enabled = canUndo) { Text(stringResource(R.string.action_undo)) }
                TextButton(onClick = onRedo, enabled = canRedo) { Text(stringResource(R.string.action_redo)) }
            }
        },
    )
}

@Composable
private fun correctionSummary(c: CorrectionView): String {
    val n = c.correction.lapNumber
    val amount = c.correction.amountMillis
    return when (c.kind) {
        CorrectionKind.REMOVE -> stringResource(R.string.correction_summary_remove, n)
        CorrectionKind.INSERT -> stringResource(R.string.correction_summary_insert, n, DurationText.stopwatch(amount))
        CorrectionKind.ADJUST -> stringResource(R.string.correction_summary_adjust, n, (if (amount >= 0) "+" else "") + DurationText.signedStopwatch(amount))
    }
}
