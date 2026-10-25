package com.relay.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.relay.app.R
import com.relay.app.util.AppLocale
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle

/** Jump far back or ahead (spec §10b): a year with arrows, then a month. "Today" goes to this month. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthPickerDialog(initial: YearMonth, onDismiss: () -> Unit, onPick: (YearMonth) -> Unit) {
    var year by rememberSaveable { mutableIntStateOf(initial.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendar_pick_month)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { year-- }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.calendar_prev_year))
                    }
                    Text(
                        year.toString(), style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { year++ }) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.calendar_next_year))
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Month.entries.forEach { m ->
                        FilterChip(
                            selected = year == initial.year && m == initial.month,
                            onClick = { onPick(YearMonth.of(year, m)) },
                            label = {
                                val name = m.getDisplayName(TextStyle.SHORT_STANDALONE, AppLocale.formatting)
                                Text(name, modifier = Modifier.width(MONTH_CHIP))
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(YearMonth.now()) }) { Text(stringResource(R.string.calendar_go_today)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Same width for every month, so the chips line up in a grid. */
private val MONTH_CHIP = 40.dp
