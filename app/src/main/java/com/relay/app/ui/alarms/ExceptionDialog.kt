package com.relay.app.ui.alarms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.relay.app.R
import com.relay.app.data.schedule.ExceptionKind
import com.relay.app.ui.common.OptionChips
import com.relay.app.util.RelativeText
import com.relay.app.util.TimeText
import java.time.LocalDate
import java.time.LocalTime

/** Add a skip / retime exception, optionally to other alarms too (spec §04). */
@Composable
fun ExceptionDialog(
    defaultHour: Int,
    defaultMinute: Int,
    is24h: Boolean,
    others: List<OtherAlarm>,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate, ExceptionKind, Int?, Int?, Set<String>) -> Unit,
) {
    val context = LocalContext.current
    var start by remember { mutableStateOf<LocalDate?>(null) }
    var end by remember { mutableStateOf<LocalDate?>(null) }
    var kind by remember { mutableStateOf(ExceptionKind.SKIP) }
    var hour by remember { mutableStateOf(defaultHour) }
    var minute by remember { mutableStateOf(defaultMinute) }
    var alsoFor by remember { mutableStateOf(emptySet<String>()) }
    var pickingDates by remember { mutableStateOf(true) }
    var pickingTime by remember { mutableStateOf(false) }

    if (pickingDates) {
        DateRangePickerDialog(
            onDismiss = { pickingDates = false; if (start == null) onDismiss() },
            onConfirm = { s, e -> start = s; end = e; pickingDates = false },
        )
        return
    }
    if (pickingTime) {
        TimePickerDialog(hour, minute, is24h, onDismiss = { pickingTime = false }) { h, m ->
            hour = h; minute = m; pickingTime = false
        }
    }
    val s = start ?: return
    val e = end ?: s

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exception_add)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = { pickingDates = true }) {
                    Text(
                        if (s == e) RelativeText.date(context, s)
                        else stringResource(R.string.exception_range, RelativeText.date(context, s), RelativeText.date(context, e)),
                    )
                }
                OptionChips(
                    title = stringResource(R.string.exception_kind),
                    options = ExceptionKind.entries,
                    selected = kind,
                    label = { stringResource(if (it == ExceptionKind.SKIP) R.string.exception_skip else R.string.exception_retime) },
                    onSelect = { kind = it },
                )
                if (kind == ExceptionKind.RETIME) {
                    OutlinedButton(onClick = { pickingTime = true }) { Text(TimeText.of(LocalTime.of(hour, minute), is24h)) }
                }
                if (others.isNotEmpty()) {
                    Text(stringResource(R.string.exception_also_for), style = MaterialTheme.typography.bodyLarge)
                    others.forEach { other ->
                        val checked = other.id in alsoFor
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { alsoFor = if (checked) alsoFor - other.id else alsoFor + other.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(
                                listOf(TimeText.of(LocalTime.of(other.hour, other.minute), is24h), other.label)
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val retime = kind == ExceptionKind.RETIME
                onConfirm(s, e, kind, hour.takeIf { retime }, minute.takeIf { retime }, alsoFor)
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
