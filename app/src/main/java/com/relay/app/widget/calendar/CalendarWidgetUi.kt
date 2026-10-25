package com.relay.app.widget.calendar

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Box
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
import com.relay.app.widget.WidgetBackground
import com.relay.app.widget.WidgetCorners
import com.relay.app.widget.WidgetFrame
import com.relay.app.widget.WidgetFrameRenderer
import com.relay.app.widget.WidgetTheme

/**
 * The widget's background, shape and border, behind its content. A plain colour is drawn natively,
 * which keeps each update small and quick (every month turn re-sends the whole widget). Only a
 * gradient or a border needs a bitmap: a small one, remembered while the widget shows, stretched
 * (not fitted, which could leave it narrower than the widget with content spilling past it).
 */
@Composable
fun FrameBackground(context: Context, frame: WidgetFrame) {
    val size = LocalSize.current
    if (frame.background != WidgetBackground.GRADIENT && frame.borderWidthDp == 0) {
        if (frame.background == WidgetBackground.TRANSPARENT) return
        val theme = WidgetTheme.of(context)
        val color = if (frame.background == WidgetBackground.SOLID) frame.backgroundColor?.toInt() ?: theme.surface else theme.surface
        val radius = when (frame.corners) {
            WidgetCorners.SHARP -> 0.dp
            WidgetCorners.ROUNDED -> WidgetFrameRenderer.ROUNDED_DP.dp
            WidgetCorners.PILL -> minOf(size.width, size.height) / 2
        }
        Box(GlanceModifier.fillMaxSize().cornerRadius(radius).background(ColorProvider(Color(color)))) {}
        return
    }
    val bitmap = remember(frame, size) {
        WidgetFrameRenderer.render(context, frame, size.width.value, size.height.value, FRAME_MAX_SIDE_PX)
    }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = GlanceModifier.fillMaxSize(),
    )
}

/** A gradient or border survives a small bitmap, stretched; a small one keeps updates quick. */
private const val FRAME_MAX_SIDE_PX = 480

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
                EntryKind.EVENT, EntryKind.PHONE -> R.string.calendar_untitled
            },
        )
    }
}
