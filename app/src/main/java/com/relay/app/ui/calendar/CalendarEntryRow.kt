package com.relay.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.relay.app.R
import com.relay.app.data.schedule.TargetKind
import com.relay.app.event.CalendarEntry
import com.relay.app.event.EntryKind

/** A row of the day list: its calendar's colour as a bar, its time or range, title and what it does. */
@Composable
fun EntryRow(entry: CalendarEntry, is24h: Boolean, onClick: () -> Unit) {
    val title = entry.title.ifBlank {
        stringResource(
            when (entry.kind) {
                EntryKind.ALARM -> R.string.calendar_alarm
                EntryKind.REMINDER -> R.string.calendar_kind_reminder
                EntryKind.EVENT, EntryKind.PHONE -> R.string.calendar_untitled
            },
        )
    }
    val detail = when (entry.kind) {
        EntryKind.EVENT -> entry.reminderLead?.let {
            stringResource(R.string.calendar_reminder_lead, CalendarText.lead(it, entry.startMinute == null, is24h))
        }
        EntryKind.REMINDER -> stringResource(R.string.calendar_kind_reminder)
        EntryKind.ALARM ->
            stringResource(if (entry.target == TargetKind.RING) R.string.calendar_alarm else R.string.calendar_scheduled_start)
        EntryKind.PHONE -> stringResource(R.string.calendar_from_phone)
    }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(Color(entry.color.toInt())))
            Row(Modifier.padding(16.dp)) {
                Text(
                    CalendarText.entryTime(LocalContext.current, entry, is24h),
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(TIME_WIDTH),
                )
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    detail?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private val TIME_WIDTH = 112.dp
