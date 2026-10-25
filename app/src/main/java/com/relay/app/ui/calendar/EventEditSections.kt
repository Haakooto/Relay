package com.relay.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.relay.app.R
import com.relay.app.data.event.EventKind
import com.relay.app.event.RepeatDraft
import com.relay.app.event.RepeatEnd
import com.relay.app.event.RepeatFreq
import com.relay.app.event.RepeatOn
import com.relay.app.event.Repeats
import com.relay.app.ui.clock.ColorChoice
import com.relay.app.ui.common.OptionChips
import com.relay.app.util.AppLocale
import com.relay.app.util.RelativeText
import java.time.LocalDate
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

/** How often (#43), on which days, and when it ends. */
@Composable
internal fun EventRepeatSection(state: EventEditState, viewModel: EventEditViewModel, onPick: (Picking) -> Unit) {
    val r = state.repeat
    OptionChips(
        title = stringResource(R.string.event_repeat),
        options = RepeatFreq.entries,
        selected = r.freq,
        label = { stringResource(freqLabel(it)) },
        onSelect = viewModel::setRepeatFreq,
    )
    if (r.freq == RepeatFreq.NONE) return
    if (r.custom != null) {
        // A rule from a calendar file the editor can't show: kept as it is until changed here.
        Text(stringResource(R.string.repeat_custom_kept), style = MaterialTheme.typography.bodyMedium)
        Text(r.custom, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        IntervalField(r, viewModel)
        RepeatDetail(state, viewModel)
    }
    RepeatEnds(r, viewModel, onPick)
}

private fun freqLabel(freq: RepeatFreq): Int = when (freq) {
    RepeatFreq.NONE -> R.string.repeat_kind_none
    RepeatFreq.DAILY -> R.string.repeat_kind_daily
    RepeatFreq.WEEKLY -> R.string.repeat_kind_weekly
    RepeatFreq.MONTHLY -> R.string.repeat_kind_monthly
    RepeatFreq.YEARLY -> R.string.repeat_kind_yearly
}

/** "Every [2] weeks". */
@Composable
private fun IntervalField(r: RepeatDraft, viewModel: EventEditViewModel) {
    val unit = when (r.freq) {
        RepeatFreq.DAILY, RepeatFreq.NONE -> R.plurals.repeat_unit_day
        RepeatFreq.WEEKLY -> R.plurals.repeat_unit_week
        RepeatFreq.MONTHLY -> R.plurals.repeat_unit_month
        RepeatFreq.YEARLY -> R.plurals.repeat_unit_year
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.repeat_every), style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = r.interval.toString(),
            onValueChange = { v ->
                v.toIntOrNull()?.let { n -> viewModel.updateRepeat { it.copy(interval = n.coerceIn(1, MAX_INTERVAL)) } }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(INTERVAL_WIDTH),
        )
        Text(pluralStringResource(unit, r.interval), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Never, on a date, or after a number of times. */
@Composable
private fun RepeatEnds(r: RepeatDraft, viewModel: EventEditViewModel, onPick: (Picking) -> Unit) {
    val context = LocalContext.current
    OptionChips(
        title = stringResource(R.string.alarm_ends),
        options = if (r.custom == null) RepeatEnd.entries else listOf(RepeatEnd.NEVER, RepeatEnd.DATE),
        selected = r.end,
        label = {
            when (it) {
                RepeatEnd.NEVER -> stringResource(R.string.alarm_ends_never)
                RepeatEnd.DATE -> r.until?.let { d -> stringResource(R.string.alarm_ends_on, RelativeText.date(context, d)) }
                    ?: stringResource(R.string.alarm_ends_date)
                RepeatEnd.COUNT -> pluralStringResource(R.plurals.repeat_ends_after, r.count, r.count)
            }
        },
        onSelect = { end ->
            if (end == RepeatEnd.DATE) onPick(Picking.UNTIL) else viewModel.updateRepeat { it.copy(end = end) }
        },
    )
    if (r.end == RepeatEnd.COUNT) {
        OutlinedTextField(
            value = r.count.toString(),
            onValueChange = { v -> v.toIntOrNull()?.let { n -> viewModel.updateRepeat { it.copy(count = n.coerceIn(1, MAX_COUNT)) } } },
            label = { Text(stringResource(R.string.repeat_times)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
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

/**
 * Weekly: the weekdays. Monthly and yearly: which day, from what the item's date allows ("day 15",
 * "the 2nd Tuesday", "the last Friday", "the last day"), with a warning when some months lack it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatDetail(state: EventEditState, viewModel: EventEditViewModel) {
    val r = state.repeat
    val date = state.date
    when (r.freq) {
        RepeatFreq.NONE, RepeatFreq.DAILY -> {}
        RepeatFreq.WEEKLY -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RelativeText.weekOrder(state.firstDay).forEach { day ->
                val on = day in r.weekdays
                FilterChip(
                    selected = on,
                    onClick = { viewModel.updateRepeat { it.copy(weekdays = if (on) it.weekdays - day else it.weekdays + day) } },
                    label = { Text(day.getDisplayName(TextStyle.SHORT, AppLocale.formatting)) },
                )
            }
        }
        RepeatFreq.MONTHLY, RepeatFreq.YEARLY -> {
            val options = Repeats.options(date)
            OptionChips(
                title = stringResource(R.string.repeat_on),
                options = options,
                selected = r.on.takeIf { it in options } ?: RepeatOn.DATE,
                label = { onLabel(it, date, yearly = r.freq == RepeatFreq.YEARLY) },
                onSelect = { on -> viewModel.updateRepeat { it.copy(on = on) } },
            )
            if (r.on == RepeatOn.DATE) DateWarning(date, yearly = r.freq == RepeatFreq.YEARLY)
        }
    }
}

/** "On day 15", "On the second Tuesday of October", "On the last day"… */
@Composable
private fun onLabel(on: RepeatOn, date: LocalDate, yearly: Boolean): String {
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, AppLocale.formatting)
    val month = date.format(DateTimeFormatter.ofPattern("LLLL", AppLocale.formatting))
    return when (on) {
        RepeatOn.DATE -> if (yearly) {
            stringResource(R.string.repeat_on_date_year, date.format(DateTimeFormatter.ofPattern("d MMMM", AppLocale.formatting)))
        } else {
            stringResource(R.string.repeat_on_date_month, date.dayOfMonth)
        }
        RepeatOn.WEEKDAY -> {
            val nth = stringResource(ORDINALS[Repeats.ordinal(date) - 1])
            if (yearly) {
                stringResource(R.string.repeat_on_weekday_year, nth, weekday, month)
            } else {
                stringResource(R.string.repeat_on_weekday_month, nth, weekday)
            }
        }
        RepeatOn.LAST_WEEKDAY ->
            if (yearly) {
                stringResource(R.string.repeat_on_last_weekday_year, weekday, month)
            } else {
                stringResource(R.string.repeat_on_last_weekday_month, weekday)
            }
        RepeatOn.LAST_DAY ->
            if (yearly) stringResource(R.string.repeat_on_last_day_year, month) else stringResource(R.string.repeat_on_last_day_month)
    }
}

private val ORDINALS = listOf(R.string.ordinal_1, R.string.ordinal_2, R.string.ordinal_3, R.string.ordinal_4)
private const val MAX_INTERVAL = 99
private const val MAX_COUNT = 999
private const val LEAP_DAY = 29
private val INTERVAL_WIDTH = 72.dp

/** A repeat on the 29th to 31st skips months without it; on 29 February, years without it. */
@Composable
private fun DateWarning(date: LocalDate, yearly: Boolean) {
    val leapDay = date.monthValue == 2 && date.dayOfMonth == LEAP_DAY
    when {
        !yearly && date.dayOfMonth > SHORTEST_MONTH -> RepeatWarning(stringResource(R.string.repeat_skips_months, date.dayOfMonth))
        yearly && leapDay -> RepeatWarning(stringResource(R.string.repeat_leap_only))
    }
}

/** Not colour alone: the warning sign says it too. */
@Composable
private fun RepeatWarning(text: String) {
    Text("⚠ $text", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
}

private const val SHORTEST_MONTH = 28
