package com.relay.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.relay.app.R
import com.relay.app.data.event.CalendarEntity
import com.relay.app.event.SubscriptionUrl
import com.relay.app.ui.clock.ColorChoice
import com.relay.app.util.RelativeText

/**
 * Subscribe to a calendar address (#33). Says plainly what Relay will fetch and when, since it's
 * the app's only network use (principles §1.3, §6.7); nothing is fetched until Subscribe.
 */
@Composable
fun SubscribeDialog(onDismiss: () -> Unit, onSubscribe: (name: String, url: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    val url = SubscriptionUrl.normalize(address)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subscribe_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.subscribe_why), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.calendar_name)) },
                )
                OutlinedTextField(
                    address, { address = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.subscribe_url)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = address.isNotBlank() && url == null,
                    supportingText = { if (address.isNotBlank() && url == null) Text(stringResource(R.string.subscribe_bad_url)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { url?.let { onSubscribe(name.trim(), it) } }, enabled = url != null && name.isNotBlank()) {
                Text(stringResource(R.string.calendars_subscribe))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A subscribed calendar: its name and colour can change; its address and items can't. */
@Composable
fun SubscriptionEditDialog(
    calendar: CalendarEntity,
    onDismiss: () -> Unit,
    onSave: (CalendarEntity) -> Unit,
    onRefresh: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf(calendar) }
    var confirming by remember { mutableStateOf(false) }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.unsubscribe_title)) },
            text = { Text(stringResource(R.string.unsubscribe_body)) },
            confirmButton = {
                TextButton(onClick = { confirming = false; onUnsubscribe() }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    draft.name, { v -> draft = draft.copy(name = v) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.calendar_name)) },
                )
                ColorChoice(stringResource(R.string.calendar_color), draft.color, CALENDAR_PALETTE, noneLabel = null) { c ->
                    c?.let { draft = draft.copy(color = it) }
                }
                Text(
                    calendar.subscriptionUrl.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val refreshed = calendar.refreshedAtMillis
                Text(
                    if (refreshed == null) {
                        stringResource(R.string.subscribe_never)
                    } else {
                        stringResource(R.string.subscribe_refreshed, RelativeText.until(context, System.currentTimeMillis() - refreshed))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row {
                    TextButton(onClick = onRefresh) { Text(stringResource(R.string.subscribe_refresh)) }
                    TextButton(onClick = { confirming = true }) { Text(stringResource(R.string.action_delete)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft.copy(name = draft.name.trim())) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
