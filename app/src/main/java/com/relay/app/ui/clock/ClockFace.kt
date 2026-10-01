package com.relay.app.ui.clock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.relay.app.clock.AnalogueRenderer
import com.relay.app.clock.ClockFonts
import com.relay.app.clock.ClockKind
import com.relay.app.clock.ClockStyle
import com.relay.app.clock.ClockText
import com.relay.app.clock.DigitalFont
import com.relay.app.clock.resolve
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

/** A ticking clock in [style]; the same drawing as the widgets (spec §08). */
@Composable
fun ClockFace(
    style: ClockStyle,
    is24hSetting: Boolean,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    digitSize: TextUnit = 72.sp,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000) // land on the second
        }
    }
    val t = Instant.ofEpochMilli(now).atZone(zone)
    val colors = MaterialTheme.colorScheme
    val resolved = style.colors.resolve(
        onSurface = colors.onSurface.toArgb(),
        muted = colors.onSurfaceVariant.toArgb(),
        accent = colors.primary.toArgb(),
        faceDefault = if (style.face == com.relay.app.clock.FaceStyle.FILLED) colors.surfaceContainerHigh.toArgb() else colors.outline.toArgb(),
    )
    val date = ClockText.date(t, style.date)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (style.kind) {
            ClockKind.DIGITAL -> {
                // Shrinks until it fits the width: a wide face (7-segment, with seconds) would clip.
                val target = if (style.showSeconds) digitSize * 0.8f else digitSize
                var fit by remember(style, target) { mutableFloatStateOf(1f) }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        ClockText.time(t, style, is24hSetting),
                        fontFamily = ClockFonts.compose(style.font),
                        fontWeight = if (style.font == DigitalFont.DISPLAY) FontWeight.SemiBold else null,
                        fontSize = target * fit,
                        color = Color(resolved.digits),
                        maxLines = 1,
                        softWrap = false,
                        onTextLayout = { if (it.didOverflowWidth && fit > 0.3f) fit *= 0.9f },
                    )
                    ClockText.amPm(t, style, is24hSetting)?.let {
                        Text(it, fontSize = digitSize * 0.3f * fit, color = Color(resolved.secondary), modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
                    }
                }
            }
            ClockKind.ANALOGUE -> {
                val context = LocalContext.current
                val numerals = remember(style.font) { ClockFonts.typeface(context, style.font) }
                Canvas(Modifier.fillMaxWidth(0.75f).aspectRatio(1f)) {
                    drawIntoCanvas { canvas ->
                        AnalogueRenderer.draw(
                            canvas.nativeCanvas, size.width, size.height,
                            t.hour, t.minute, t.second.toFloat(), style, resolved, numerals,
                        )
                    }
                }
            }
        }
        if (date != null) Text(date, style = MaterialTheme.typography.titleLarge, color = Color(resolved.secondary))
    }
}
