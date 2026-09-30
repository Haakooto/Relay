package com.clockr.app.ui.screens

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.clockr.app.data.ClockConfigEntity
import com.clockr.app.data.ClockDigitalFont
import com.clockr.app.data.ClockStyle
import com.clockr.app.data.ClockrDatabase
import com.clockr.app.ui.clock.AnalogueClockSpec
import com.clockr.app.ui.clock.clockHandAngles
import com.clockr.app.ui.clock.toComposeColor
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.cos
import kotlin.math.sin

/**
 * Live full-screen face for one [ClockConfigEntity] — the one surface not throttled to minute
 * granularity (unlike the widget), and the default tap target for every home-screen widget.
 * Digital or analogue per the config; background always follows the app theme here (no separate
 * widget-style background setting in-app).
 */
@Composable
fun ClockScreen(configId: String, modifier: Modifier = Modifier, is24Hour: Boolean = true) {
    val application = LocalContext.current.applicationContext as Application
    val config by produceState<ClockConfigEntity?>(initialValue = null, configId) {
        value = ClockrDatabase.get(application).clockConfigDao().findById(configId)
    }

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }

    Scaffold(modifier = modifier) { padding ->
        val current = config
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            if (current == null) {
                CircularProgressIndicator()
            } else if (current.style == ClockStyle.DIGITAL) {
                DigitalFace(current, now, is24Hour)
            } else {
                AnalogueFace(current, now)
            }
        }
    }
}

@Composable
private fun DigitalFace(config: ClockConfigEntity, now: LocalDateTime, is24Hour: Boolean) {
    val timeFormatter = remember(is24Hour, config.showSeconds) {
        val pattern = when {
            is24Hour && config.showSeconds -> "HH:mm:ss"
            is24Hour -> "HH:mm"
            config.showSeconds -> "hh:mm:ss a"
            else -> "hh:mm a"
        }
        DateTimeFormatter.ofPattern(pattern)
    }
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val color = config.color.toComposeColor()
    val fontFamily = when (config.digitalFont) {
        ClockDigitalFont.DEFAULT -> FontFamily.Default
        ClockDigitalFont.SERIF -> FontFamily.Serif
        ClockDigitalFont.MONOSPACE -> FontFamily.Monospace
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            now.format(timeFormatter),
            style = MaterialTheme.typography.displayLarge.copy(fontFamily = fontFamily),
            color = color,
        )
        if (config.showDate) {
            Text(
                now.format(dateFormatter),
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = fontFamily),
                color = color,
            )
        }
    }
}

@Composable
private fun AnalogueFace(config: ClockConfigEntity, now: LocalDateTime) {
    val spec = remember(config.analogueVariant) { AnalogueClockSpec.forVariant(config.analogueVariant) }
    val angles = remember(now) { clockHandAngles(now.toLocalTime()) }
    val color = config.color.toComposeColor()

    Canvas(modifier = Modifier.fillMaxWidth(0.7f).aspectRatio(1f).padding(24.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2f - 12f

        if (spec.showFaceRing) {
            drawCircle(color = color, radius = radius, style = Stroke(width = 4f))
        }

        repeat(spec.tickCount) { i ->
            val isMajor = i % spec.majorTickEvery == 0
            val angle = Math.toRadians((i * 360.0 / spec.tickCount) - 90.0)
            val outer = radius - 6f
            val inner = outer - if (isMajor) 16f else 8f
            drawLine(
                color = color,
                start = Offset(cx + (inner * cos(angle)).toFloat(), cy + (inner * sin(angle)).toFloat()),
                end = Offset(cx + (outer * cos(angle)).toFloat(), cy + (outer * sin(angle)).toFloat()),
                strokeWidth = if (isMajor) 5f else 2.5f,
            )
        }

        fun drawHand(angleDeg: Float, lengthFraction: Float, width: Float) {
            val angle = Math.toRadians(angleDeg.toDouble() - 90.0)
            val length = radius * lengthFraction
            drawLine(
                color = color,
                start = Offset(cx, cy),
                end = Offset(cx + (length * cos(angle)).toFloat(), cy + (length * sin(angle)).toFloat()),
                strokeWidth = width * spec.handWidthScale,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
        drawHand(angles.hourDeg, spec.hourHandLength, 10f)
        drawHand(angles.minuteDeg, spec.minuteHandLength, 7f)
        if (config.showSeconds) drawHand(angles.secondDeg, spec.minuteHandLength, 2.5f)

        if (spec.showCenterDot) drawCircle(color = color, radius = 7f)
    }
}
