package com.relay.app.ui.clock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.relay.app.R
import com.relay.app.clock.Cities
import com.relay.app.clock.City
import java.time.ZoneId

/** Search the time-zone database by city (spec §08). [extra] adds options above the list. */
@Composable
fun CityPickerDialog(title: String, onDismiss: () -> Unit, extra: (@Composable () -> Unit)? = null, onPick: (City) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { Cities.search(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.clock_search_city)) },
                )
                extra?.invoke()
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(results, key = { it.zoneId }) { city ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(city) }.padding(vertical = 10.dp)) {
                            Text(city.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${city.region} · ${offsetLabel(ZoneId.of(city.zoneId))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** "UTC+5:30" style. */
fun offsetLabel(zone: ZoneId): String {
    val total = zone.rules.getOffset(java.time.Instant.now()).totalSeconds / 60
    val sign = if (total < 0) "−" else "+"
    val h = kotlin.math.abs(total) / 60
    val m = kotlin.math.abs(total) % 60
    return "UTC$sign$h" + if (m > 0) ":%02d".format(m) else ""
}
