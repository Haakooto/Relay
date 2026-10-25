package com.relay.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.relay.app.R
import com.relay.app.data.event.EventKind
import com.relay.app.data.event.RepeatKind
import com.relay.app.ui.clock.ColorChoice
import com.relay.app.ui.common.OptionChips
import com.relay.app.util.AppLocale
import com.relay.app.util.RelativeText
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** Which calendar it's in (#34), and its own colour if it shouldn't take the calendar's. */
@Composable
internal fun EventCalendarSection(state: EventEditState, viewModel: EventEditViewModel) {
    CalendarPicker(state.calendars, state.calendarId, viewModel::setCalendar)
    ColorChoice(
        stringResource(R.string.event_color), state.color, CALENDAR_PALETTE, noneLabel = stringResource(R.string.event_color_calendar),
    ) { c -> viewModel.update { it.copy(color = c) } }
}

/** Date, all-day, start, and (events only) end time and last day. */
@Composable
internal fun EventWhenSection(state: EventEditState, viewModel: EventEditViewModel, onPick: (Picking) -> Unit) {
    val context = LocalContext.current
    val isEvent = state.kind == EventKind.EVENT
    OutlinedButton(onClick = { onPick(Picking.DATE) }) { Text(RelativeText.date(context, state.date)) }
    if (isEvent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.calendar_all_day), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = state.allDay, onCheckedChange = viewModel::setAllDay)
        }
    }
    if (!isEvent || !state.allDay) {
        val label = stringResource(if (isEvent) R.string.event_starts else R.string.event_at)
        LabelledButton(label, CalendarText.time(state.startMinute, state.is24h), onClick = { onPick(Picking.START) })
    }
    if (!isEvent) return
    if (!state.allDay) {
        LabelledButton(
            stringResource(R.string.event_ends),
            state.endMinute?.let { CalendarText.time(it, state.is24h) } ?: stringResource(R.string.event_end_none),
            onClick = { onPick(Picking.END_TIME) },
            onClear = if (state.endMinute != null) ({ viewModel.update { it.copy(endMinute = null) } }) else null,
            clearLabel = stringResource(R.string.event_end_none),
        )
    }
    LabelledButton(
        stringResource(R.string.event_end_day),
        RelativeText.date(context, state.endDate ?: state.date),
        onClick = { onPick(Picking.END_DATE) },
        onClear = if (state.endDate != null && state.endDate != state.date) ({ viewModel.update { it.copy(endDate = null) } }) else null,
        clearLabel = stringResource(R.string.event_end_same_day),
    )
    if (state.endsBeforeStart) {
        Text(
            stringResource(R.string.event_end_before_start),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** Repeat kind, its detail, and when it ends. */
@Composable
internal fun EventRepeatSection(state: EventEditState, viewModel: EventEditViewModel, onPick: (Picking) -> Unit) {
    val context = LocalContext.current
    OptionChips(
        title = stringResource(R.string.event_repeat),
        options = RepeatKind.entries,
        selected = state.repeatKind,
        label = { stringResource(repeatLabel(it)) },
        onSelect = viewModel::setRepeat,
    )
    RepeatDetail(state, viewModel)
    if (state.repeatKind == RepeatKind.NONE) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.until == null, onClick = { viewModel.update { it.copy(until = null) } },
            label = { Text(stringResource(R.string.alarm_ends_never)) },
        )
        FilterChip(
            selected = state.until != null, onClick = { onPick(Picking.UNTIL) },
            label = {
                Text(
                    state.until?.let { stringResource(R.string.alarm_ends_on, RelativeText.date(context, it)) }
                        ?: stringResource(R.string.alarm_ends_date),
                )
            },
        )
    }
}

private fun repeatLabel(kind: RepeatKind): Int = when (kind) {
    RepeatKind.NONE -> R.string.repeat_kind_none
    RepeatKind.WEEKLY -> R.string.repeat_kind_weekly
    RepeatKind.MONTHLY -> R.string.repeat_kind_monthly
    RepeatKind.YEARLY -> R.string.repeat_kind_yearly
}

/** A field label with its value as a button, "Starts  [10:00]", and an optional clear button. */
@Composable
private fun LabelledButton(label: String, value: String, onClick: () -> Unit, onClear: (() -> Unit)? = null, clearLabel: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onClick) { Text(value) }
        if (onClear != null) {
            IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, contentDescription = clearLabel) }
        }
    }
}

/** Weekly: the weekdays. Monthly and yearly: what that means, and a warning when some months lack the day. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatDetail(state: EventEditState, viewModel: EventEditViewModel) {
    val dayOfMonth = state.date.dayOfMonth
    when (state.repeatKind) {
        RepeatKind.NONE -> {}
        RepeatKind.WEEKLY -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RelativeText.weekOrder(state.firstDay).forEach { day ->
                val on = day in state.repeatDays
                FilterChip(
                    selected = on,
                    onClick = { viewModel.update { it.copy(repeatDays = if (on) it.repeatDays - day else it.repeatDays + day) } },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, AppLocale.formatting)) },
                )
            }
        }
        RepeatKind.MONTHLY -> {
            Text(stringResource(R.string.repeat_monthly_on, dayOfMonth), style = MaterialTheme.typography.bodyLarge)
            if (dayOfMonth > SHORTEST_MONTH) RepeatWarning(stringResource(R.string.repeat_skips_months, dayOfMonth))
        }
        RepeatKind.YEARLY -> {
            val date = state.date.format(DateTimeFormatter.ofPattern("d MMMM", AppLocale.formatting))
            Text(stringResource(R.string.repeat_yearly_on, date), style = MaterialTheme.typography.bodyLarge)
            if (state.date.monthValue == 2 && dayOfMonth == 29) RepeatWarning(stringResource(R.string.repeat_leap_only))
        }
    }
}

/** Not colour alone: the warning sign says it too. */
@Composable
private fun RepeatWarning(text: String) {
    Text("⚠ $text", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
}

private const val SHORTEST_MONTH = 28
