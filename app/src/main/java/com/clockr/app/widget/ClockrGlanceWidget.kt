package com.clockr.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.clockr.app.MainActivity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val EXTRA_OPEN_CLOCK = "com.clockr.app.extra.OPEN_CLOCK"

/**
 * Spec §07 — minute-granularity digital face; live seconds stay in-app (ClockScreen). Tapping
 * opens the app straight to the Clock pane, not wherever it last was. Visual customization (the
 * axes in §07's table) is future work — this is the plain baseline.
 */
class ClockrGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val now = LocalDateTime.now()
            val timeText = now.format(DateTimeFormatter.ofPattern("HH:mm"))
            val dateText = now.format(DateTimeFormatter.ofPattern("EEE, MMM d"))

            val openClockIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_OPEN_CLOCK, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF1C1E17))
                    .clickable(actionStartActivity(openClockIntent)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        timeText,
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFEDEAE0)),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    Text(
                        dateText,
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFA9A491)),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        }
    }
}
