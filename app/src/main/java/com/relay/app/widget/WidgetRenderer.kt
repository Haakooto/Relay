package com.relay.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.relay.app.clock.AnalogueRenderer
import com.relay.app.clock.ClockFonts
import com.relay.app.clock.ClockKind
import com.relay.app.clock.ClockText
import com.relay.app.clock.FaceStyle
import com.relay.app.clock.resolve
import java.time.Instant
import java.time.ZoneId
import kotlin.math.min

/**
 * Draws a whole widget into a bitmap: background, shape, border and the clock. Glance can't use
 * bundled fonts, gradients or a Canvas, so a bitmap is what makes widgets look like the in-app
 * clock. Redrawn once a minute by [WidgetTicker].
 */
object WidgetRenderer {
    fun render(context: Context, spec: WidgetSpec, widthDp: Float, heightDp: Float, is24hSetting: Boolean, nowMillis: Long): Bitmap {
        val px = WidgetFrameRenderer.pxPerDp(context, widthDp, heightDp)
        val w = (widthDp * px).toInt().coerceAtLeast(1)
        val h = (heightDp * px).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val theme = WidgetTheme.of(context)
        val surface = theme.surface
        val onSurface = theme.onSurface
        val muted = theme.muted
        val accent = theme.accent
        WidgetFrameRenderer.draw(canvas, spec.frame, w, h, px, theme)

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
                if (caption != null) drawCentred(canvas, caption, w / 2f, h - pad - captionH * 0.2f, captionH * 0.7f, colors.secondary, typeface, w - 2 * pad)
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
                if (caption != null) drawCentred(canvas, caption, w / 2f, top + timeSize + captionSize * 1.15f, captionSize, colors.secondary, typeface, w - 2 * pad)
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
