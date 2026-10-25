package com.relay.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.relay.app.R
import com.relay.app.event.CalendarEntry
import com.relay.app.event.CalendarViews
import com.relay.app.event.DAYS_PER_WEEK
import com.relay.app.event.GRID_DAYS
import com.relay.app.event.SpanSegment
import com.relay.app.util.AppLocale
import com.relay.app.util.RelativeText
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * Six weeks; swipe sideways to turn the month. Each week has its day numbers with a coloured dot
 * per single-day entry, then its multi-day items as bars across their days (#35). Days outside
 * the month are dimmed but tappable.
 */
@Composable
fun MonthGrid(s: CalendarState, onSelect: (LocalDate) -> Unit, onTurn: (Long) -> Unit, onOpen: (CalendarEntry) -> Unit) {
    var drag by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier.fillMaxWidth().pointerInput(s.month) {
            detectHorizontalDragGestures(
                onDragStart = { drag = 0f },
                onDragEnd = {
                    when {
                        drag < -SWIPE_PX -> onTurn(1)
                        drag > SWIPE_PX -> onTurn(-1)
                    }
                },
            ) { _, dx -> drag += dx }
        },
    ) {
        Row {
            RelativeText.weekOrder(s.firstDay).forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.SHORT, AppLocale.formatting),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        for (week in 0 until (GRID_DAYS / DAYS_PER_WEEK).toInt()) {
            val weekStart = s.gridStart.plusDays(week * DAYS_PER_WEEK.toLong())
            Row {
                for (d in 0 until DAYS_PER_WEEK) DayCell(s, weekStart.plusDays(d.toLong()), onSelect)
            }
            CalendarViews.weekLanes(weekStart, s.entries).forEach { lane -> SpanLane(lane, onOpen) }
        }
    }
}

/** One lane of bars: gaps and bars weighted by the days they cover. */
@Composable
private fun SpanLane(lane: List<SpanSegment>, onOpen: (CalendarEntry) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        var col = 0
        lane.forEach { segment ->
            if (segment.startCol > col) Spacer(Modifier.weight((segment.startCol - col).toFloat()))
            Box(
                Modifier.weight((segment.endCol - segment.startCol + 1).toFloat()).padding(horizontal = 1.dp).heightIn(min = BAR_HEIGHT)
                    .background(Color(segment.entry.color.toInt()), RoundedCornerShape(4.dp))
                    .clickable { onOpen(segment.entry) }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    segment.entry.title.ifBlank { context.getString(R.string.calendar_untitled) },
                    color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            col = segment.endCol + 1
        }
        if (col < DAYS_PER_WEEK) Spacer(Modifier.weight((DAYS_PER_WEEK - col).toFloat()))
    }
}

/** One day: its number (filled when selected, ringed when today) and a dot per single-day entry, in its colour. */
@Composable
private fun RowScope.DayCell(s: CalendarState, day: LocalDate, onSelect: (LocalDate) -> Unit) {
    val entries = s.entries[day].orEmpty()
    val isSelected = day == s.selected
    val label = RelativeText.date(LocalContext.current, day, s.today)
    val description = pluralStringResource(R.plurals.calendar_day_items, entries.size, label, entries.size)
    Column(
        Modifier.weight(1f).heightIn(min = 48.dp)
            .clickable { onSelect(day) }
            .clearAndSetSemantics { contentDescription = description; selected = isSelected },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val ring = if (day == s.today && !isSelected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier
        Box(
            Modifier.padding(top = 4.dp).size(32.dp)
                .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                .then(ring),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (day == s.today) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    day.month == s.month.month -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                },
            )
        }
        Row(Modifier.padding(vertical = 3.dp).height(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            entries.filter { !it.isMultiDay }.take(MAX_DOTS).forEach {
                Box(Modifier.size(5.dp).background(Color(it.color.toInt()), CircleShape))
            }
        }
    }
}

private const val MAX_DOTS = 3
private const val SWIPE_PX = 120f
private val BAR_HEIGHT = 18.dp
