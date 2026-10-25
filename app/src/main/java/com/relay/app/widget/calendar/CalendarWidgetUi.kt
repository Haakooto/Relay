package com.relay.app.widget.calendar

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.relay.app.R
import com.relay.app.data.schedule.TargetKind
import com.relay.app.event.CalendarEntry
import com.relay.app.event.EntryKind
import com.relay.app.ui.calendar.CalendarText
import com.relay.app.widget.WidgetFrame
import com.relay.app.widget.WidgetFrameRenderer

/**
 * The widget's background, shape and border, stretched over the whole widget; content goes on
 * top. Stretched, not fitted: Glance's default (fit) can leave the frame narrower than the widget
 * and centred, with the content spilling past its edges.
 */
@Composable
fun FrameBackground(context: Context, frame: WidgetFrame) {
    val size = LocalSize.current
    Image(
        provider = ImageProvider(WidgetFrameRenderer.render(context, frame, size.width.value, size.height.value)),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.fillMaxSize(),
    )
}

/** Single-line widget text in one of the [WidgetColors]. */
@Composable
fun WidgetText(
    text: String,
    color: Int,
    size: TextUnit,
    modifier: GlanceModifier = GlanceModifier,
    bold: Boolean = false,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text,
        modifier = modifier,
        maxLines = 1,
        style = TextStyle(
            color = ColorProvider(Color(color)),
            fontSize = size,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = align,
        ),
    )
}

/** Text for an entry's row: its time or range, and its title or a stand-in. */
object EntryText {
    fun time(context: Context, entry: CalendarEntry, is24h: Boolean): String = CalendarText.entryTime(context, entry, is24h)

    fun title(context: Context, entry: CalendarEntry): String = entry.title.ifBlank {
        context.getString(
            when (entry.kind) {
                EntryKind.ALARM -> if (entry.target == TargetKind.RING) R.string.calendar_alarm else R.string.calendar_scheduled_start
                EntryKind.REMINDER -> R.string.calendar_kind_reminder
                EntryKind.EVENT -> R.string.calendar_untitled
            },
        )
    }
}
