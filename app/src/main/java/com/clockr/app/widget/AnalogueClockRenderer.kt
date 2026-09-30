package com.clockr.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.clockr.app.data.ClockColor
import com.clockr.app.data.ClockConfigEntity
import com.clockr.app.ui.clock.AnalogueClockSpec
import com.clockr.app.ui.clock.clockHandAngles
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.sin

/** ARGB ints matching ui/theme/Color.kt's brand palette — a separate mapping since this file
 *  draws with android.graphics (Glance can't render Compose Canvas into RemoteViews), not Compose. */
private fun ClockColor.toArgb(): Int = when (this) {
    ClockColor.INK -> 0xFF151513.toInt()
    ClockColor.ACCENT -> 0xFFC7501E.toInt()
    ClockColor.TEAL -> 0xFF2E6F66.toInt()
    ClockColor.BRASS -> 0xFF7A6A4F.toInt()
}

/**
 * Renders one analogue clock face to a small bitmap using plain `android.graphics.Canvas` — the
 * standard technique for an analogue widget face, since Glance/RemoteViews has no arbitrary
 * rotation or Canvas support. Called once per minute tick (same cadence [WidgetTickScheduler]
 * already drives the digital face at), so this stays cheap: one small bitmap, drawn with a
 * handful of primitives, no continuous animation.
 */
object AnalogueClockRenderer {
    private const val SIZE_PX = 160

    fun render(config: ClockConfigEntity, time: LocalDateTime): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val spec = AnalogueClockSpec.forVariant(config.analogueVariant)
        val angles = clockHandAngles(time.toLocalTime())
        val color = config.color.toArgb()
        val cx = SIZE_PX / 2f
        val cy = SIZE_PX / 2f
        val radius = SIZE_PX / 2f - 6f

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.color = color
            strokeCap = Paint.Cap.ROUND
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }

        if (spec.showFaceRing) {
            stroke.strokeWidth = 2.5f
            canvas.drawCircle(cx, cy, radius, stroke)
        }

        repeat(spec.tickCount) { i ->
            val isMajor = i % spec.majorTickEvery == 0
            val angle = Math.toRadians((i * 360.0 / spec.tickCount) - 90.0)
            val outer = radius - 4f
            val inner = outer - if (isMajor) 10f else 5f
            stroke.strokeWidth = if (isMajor) 3f else 1.5f
            canvas.drawLine(
                (cx + inner * cos(angle)).toFloat(), (cy + inner * sin(angle)).toFloat(),
                (cx + outer * cos(angle)).toFloat(), (cy + outer * sin(angle)).toFloat(),
                stroke,
            )
        }

        if (spec.showNumerals) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = radius * 0.18f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            listOf(12, 3, 6, 9).forEach { hour ->
                val angle = Math.toRadians(hour * 30.0 - 90.0)
                val r = radius * 0.78f
                val x = (cx + r * cos(angle)).toFloat()
                val y = (cy + r * sin(angle)).toFloat() - (textPaint.ascent() + textPaint.descent()) / 2
                canvas.drawText(hour.toString(), x, y, textPaint)
            }
        }

        fun drawHand(angleDeg: Float, lengthFraction: Float, widthPx: Float) {
            stroke.strokeWidth = widthPx * spec.handWidthScale
            val angle = Math.toRadians(angleDeg.toDouble() - 90.0)
            val length = radius * lengthFraction
            canvas.drawLine(
                cx, cy,
                (cx + length * cos(angle)).toFloat(), (cy + length * sin(angle)).toFloat(),
                stroke,
            )
        }
        drawHand(angles.hourDeg, spec.hourHandLength, 6f)
        drawHand(angles.minuteDeg, spec.minuteHandLength, 4f)
        if (config.showSeconds) drawHand(angles.secondDeg, spec.minuteHandLength, 1.5f)

        if (spec.showCenterDot) canvas.drawCircle(cx, cy, 4f, fill)

        return bitmap
    }
}
