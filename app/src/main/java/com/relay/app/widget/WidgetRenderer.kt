package com.relay.app.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import com.relay.app.clock.AnalogueRenderer
import com.relay.app.clock.ClockFonts
import com.relay.app.clock.ClockKind
import com.relay.app.clock.ClockText
import com.relay.app.clock.FaceStyle
import com.relay.app.clock.resolve
import com.relay.app.ui.theme.Accent
import com.relay.app.ui.theme.Brass
import com.relay.app.ui.theme.DarkTokens
import com.relay.app.ui.theme.LightTokens
import java.time.Instant
import java.time.ZoneId
import kotlin.math.min

/**
 * Draws a whole widget into a bitmap: background, shape, border and the clock. Glance can't use
 * bundled fonts, gradients or a Canvas, so a bitmap is what makes widgets look like the in-app
 * clock. Redrawn once a minute by [WidgetTicker].
 */
object WidgetRenderer {
    /** Keeps memory and the RemoteViews bitmap budget in check on big widgets. */
    private const val MAX_SIDE_PX = 1200

    fun render(context: Context, spec: WidgetSpec, widthDp: Float, heightDp: Float, is24hSetting: Boolean, nowMillis: Long): Bitmap {
        val density = context.resources.displayMetrics.density
        val scale = min(1f, MAX_SIDE_PX / (maxOf(widthDp, heightDp) * density))
        val px = density * scale
        val w = (widthDp * px).toInt().coerceAtLeast(1)
        val h = (heightDp * px).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val surface = (if (dark) DarkTokens.surfaceContainer else LightTokens.surfaceContainerLow).toArgb()
        val onSurface = (if (dark) DarkTokens.onSurface else LightTokens.onSurface).toArgb()
        val muted = (if (dark) DarkTokens.onSurfaceVariant else LightTokens.onSurfaceVariant).toArgb()
        val accent = (if (dark) DarkTokens.primary else LightTokens.primary).toArgb()

        // Shape, background, border.
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val radius = when (spec.corners) {
            WidgetCorners.SHARP -> 0f
            WidgetCorners.ROUNDED -> 20f * px
            WidgetCorners.PILL -> min(w, h) / 2f
        }
        val shape = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(shape)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        when (spec.background) {
            WidgetBackground.TRANSPARENT -> {}
            WidgetBackground.FOLLOW_THEME -> { bg.color = surface; canvas.drawRect(rect, bg) }
            WidgetBackground.SOLID -> { bg.color = spec.backgroundColor?.toInt() ?: surface; canvas.drawRect(rect, bg) }
            WidgetBackground.GRADIENT -> {
                bg.shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    spec.backgroundColor?.toInt() ?: Accent.toArgb(), spec.backgroundColor2?.toInt() ?: Brass.toArgb(),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRect(rect, bg)
            }
        }
        canvas.restore()
        if (spec.borderWidthDp > 0) {
            val stroke = spec.borderWidthDp * px
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                color = spec.borderColor?.toInt() ?: accent
            }
            val inset = RectF(stroke / 2, stroke / 2, w - stroke / 2, h - stroke / 2)
            canvas.drawRoundRect(inset, maxOf(0f, radius - stroke / 2), maxOf(0f, radius - stroke / 2), border)
        }

        // The clock.
        val style = spec.style
        val zone = spec.zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        val t = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val colors = style.colors.resolve(onSurface, muted, accent, if (style.face == FaceStyle.FILLED) surface else muted)
        val typeface = ClockFonts.typeface(context, style.font)
        val caption = listOfNotNull(spec.city, ClockText.date(t, style.date)).joinToString(" · ").ifBlank { null }
        val pad = 8f * px

        when (style.kind) {
            ClockKind.ANALOGUE -> {
                val captionH = if (caption != null) h * 0.16f else 0f
                val side = min(w - 2 * pad, h - 2 * pad - captionH)
                canvas.save()
                canvas.translate((w - side) / 2, pad)
                AnalogueRenderer.draw(canvas, side, side, t.hour, t.minute, 0f, style.copy(secondHand = false), colors, typeface)
                canvas.restore()
                if (caption != null) drawCentred(canvas, caption, w / 2f, h - pad - captionH * 0.2f, captionH * 0.7f, colors.secondary, null, w - 2 * pad)
            }
            ClockKind.DIGITAL -> {
                val time = ClockText.time(t, style, is24hSetting, withSeconds = false)
                val amPm = ClockText.amPm(t, style, is24hSetting)
                val text = if (amPm != null) "$time $amPm" else time
                val timeArea = if (caption != null) h * 0.62f else h * 0.8f
                val timeSize = fitSize(text, typeface, w - 2 * pad, timeArea)
                val captionSize = timeSize * 0.3f
                val block = timeSize + if (caption != null) captionSize * 1.4f else 0f
                val top = (h - block) / 2
                drawCentred(canvas, text, w / 2f, top + timeSize * 0.85f, timeSize, colors.digits, typeface, w - 2 * pad)
                if (caption != null) drawCentred(canvas, caption, w / 2f, top + timeSize + captionSize * 1.15f, captionSize, colors.secondary, null, w - 2 * pad)
            }
        }
        return bitmap
    }

    /** Largest size where [text] fits [maxWidth] and the line fits [maxHeight]. */
    private fun fitSize(text: String, typeface: android.graphics.Typeface, maxWidth: Float, maxHeight: Float): Float {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = 100f }
        val width = p.measureText(text).coerceAtLeast(1f)
        return min(100f * maxWidth / width, maxHeight)
    }

    private fun drawCentred(canvas: Canvas, text: String, x: Float, baseline: Float, size: Float, color: Int, typeface: android.graphics.Typeface?, maxWidth: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            textAlign = Paint.Align.CENTER
            if (typeface != null) this.typeface = typeface
        }
        val width = p.measureText(text)
        if (width > maxWidth) p.textSize = size * maxWidth / width
        canvas.drawText(text, x, baseline, p)
    }
}
