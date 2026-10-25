package com.relay.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.relay.app.R
import com.relay.app.data.event.CalendarEntity

/** "Calendar": one chip per calendar, each with its colour (#34). Used by the item and alarm editors. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarPicker(calendars: List<CalendarEntity>, selected: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.event_calendar), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            calendars.forEach { calendar ->
                FilterChip(
                    selected = calendar.id == selected,
                    onClick = { onSelect(calendar.id) },
                    label = { Text(calendarName(calendar)) },
                    leadingIcon = { Box(Modifier.size(12.dp).background(Color(calendar.color.toInt()), CircleShape)) },
                )
            }
        }
    }
}
