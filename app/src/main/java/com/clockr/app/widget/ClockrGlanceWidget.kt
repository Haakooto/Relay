package com.clockr.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.clockr.app.MainActivity
import com.clockr.app.data.ClockBackground
import com.clockr.app.data.ClockConfigEntity
import com.clockr.app.data.ClockCornerStyle
import com.clockr.app.data.ClockDigitalFont
import com.clockr.app.data.ClockStyle
import com.clockr.app.data.ClockrDatabase
import com.clockr.app.ui.clock.toComposeColor
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val EXTRA_OPEN_CLOCK = "com.clockr.app.extra.OPEN_CLOCK"

private fun ClockDigitalFont.toGlanceFontFamily(): FontFamily = when (this) {
    ClockDigitalFont.DEFAULT -> FontFamily.SansSerif
    ClockDigitalFont.SERIF -> FontFamily.Serif
    ClockDigitalFont.MONOSPACE -> FontFamily.Monospace
}

/**
 * Spec §07 — one independently-styled instance per placed widget (see [ClockConfigActivity]),
 * digital or analogue (bitmap-rendered, see [AnalogueClockRenderer]), still minute-granularity —
 * live seconds stay in-app (ClockScreen). Tapping opens the app straight to the Clock pane.
 */
class ClockrGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = ClockrDatabase.get(context).clockConfigDao().findByAppWidgetId(appWidgetId)
            ?: ClockConfigEntity.default(id = "widget-$appWidgetId-fallback", appWidgetId = appWidgetId)

        provideContent {
            val now = LocalDateTime.now()
            val openClockIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_OPEN_CLOCK, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            WidgetSurface(config = config, onClick = actionStartActivity(openClockIntent)) {
                when (config.style) {
                    ClockStyle.DIGITAL -> DigitalWidgetFace(config, now)
                    ClockStyle.ANALOGUE -> {
                        val bitmap = remember(config, now.minute) { AnalogueClockRenderer.render(config, now) }
                        Image(ImageProvider(bitmap), contentDescription = null)
                    }
                }
            }
        }
    }
}

/** Background/corner-radius/border, shared by both the digital and analogue faces so that
 *  styling logic exists exactly once regardless of [ClockConfigEntity.style]. The border is
 *  faked with a nested-Box padding trick — Glance/RemoteViews has no native border modifier. */
@Composable
private fun WidgetSurface(config: ClockConfigEntity, onClick: androidx.glance.action.Action, content: @Composable () -> Unit) {
    val corner = when (config.cornerStyle) {
        ClockCornerStyle.SHARP -> 0.dp
        ClockCornerStyle.ROUNDED -> 16.dp
        ClockCornerStyle.PILL -> 48.dp
    }
    val innerModifier = GlanceModifier.fillMaxSize()
        .let {
            when (config.background) {
                ClockBackground.TRANSPARENT -> it
                ClockBackground.SOLID -> it.background(config.backgroundColor.toComposeColor())
                ClockBackground.FOLLOW_THEME -> it.background(Color(0xFF1C1E17))
            }
        }
        .cornerRadius(corner)

    if (config.borderEnabled) {
        Box(
            modifier = GlanceModifier.fillMaxSize()
                .background(config.color.toComposeColor())
                .cornerRadius(corner)
                .padding(config.borderWidthDp.dp)
                .clickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = innerModifier, contentAlignment = Alignment.Center) { content() }
        }
    } else {
        Box(modifier = innerModifier.clickable(onClick), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun DigitalWidgetFace(config: ClockConfigEntity, now: LocalDateTime) {
    val timeText = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val dateText = now.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    val color = config.color.toComposeColor()
    val fontFamily = config.digitalFont.toGlanceFontFamily()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            timeText,
            style = TextStyle(
                color = ColorProvider(color),
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = fontFamily,
                textAlign = TextAlign.Center,
            ),
        )
        if (config.showDate) {
            Text(
                dateText,
                style = TextStyle(
                    color = ColorProvider(color),
                    fontSize = 12.sp,
                    fontFamily = fontFamily,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}
