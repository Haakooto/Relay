package com.relay.app.clock

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Concrete ARGB colours for one drawing, with theme defaults already filled in. */
data class ResolvedClockColors(
    val digits: Int,
    val secondary: Int,
    val hourHand: Int,
    val minuteHand: Int,
    val secondHand: Int,
    val ticks: Int,
    val face: Int,
)

fun ClockColors.resolve(onSurface: Int, muted: Int, accent: Int, faceDefault: Int) = ResolvedClockColors(
    digits = digits?.toInt() ?: onSurface,
    secondary = secondary?.toInt() ?: muted,
    hourHand = hourHand?.toInt() ?: onSurface,
    minuteHand = minuteHand?.toInt() ?: onSurface,
    secondHand = secondHand?.toInt() ?: accent,
    ticks = ticks?.toInt() ?: muted,
    face = face?.toInt() ?: faceDefault,
)

/**
 * Draws an analogue face on a plain android.graphics.Canvas: the one renderer behind both the
 * in-app clock (through Compose's native canvas) and widget bitmaps, so they always agree.
 * Angles in degrees clockwise from 12.
 */
object AnalogueRenderer {
    fun draw(
        canvas: Canvas, width: Float, height: Float,
        hours: Int, minutes: Int, seconds: Float,
        style: ClockStyle, colors: ResolvedClockColors, numeralTypeface: Typeface? = null,
    ) {
        val cx = width / 2
        val cy = height / 2
        val r = min(width, height) / 2 * 0.96f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (style.face) {
            FaceStyle.NONE -> {}
            FaceStyle.RING, FaceStyle.NUMERALS -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = r * 0.025f
                paint.color = colors.face
                canvas.drawCircle(cx, cy, r - paint.strokeWidth / 2, paint)
            }
            FaceStyle.FILLED -> {
                paint.style = Paint.Style.FILL
                paint.color = colors.face
                canvas.drawCircle(cx, cy, r, paint)
            }
        }

        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = colors.ticks
        fun tick(index: Int, of: Int, length: Float, width: Float) {
            val a = Math.toRadians(index * 360.0 / of)
            val outer = r * 0.9f
            paint.strokeWidth = width
            canvas.drawLine(
                cx + (outer * sin(a)).toFloat(), cy - (outer * cos(a)).toFloat(),
                cx + ((outer - length) * sin(a)).toFloat(), cy - ((outer - length) * cos(a)).toFloat(), paint,
            )
        }
        when (style.ticks) {
            TickStyle.NONE -> {}
            TickStyle.MINUTES -> for (i in 0 until 60) {
                if (i % 5 == 0) tick(i, 60, r * 0.1f, r * 0.03f) else tick(i, 60, r * 0.04f, r * 0.012f)
            }
            TickStyle.HOURS -> for (i in 0 until 12) tick(i, 12, r * 0.1f, r * 0.03f)
            TickStyle.QUARTERS -> for (i in 0 until 4) tick(i, 4, r * 0.14f, r * 0.045f)
        }

        if (style.face == FaceStyle.NUMERALS) {
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors.digits
                textSize = r * 0.17f
                textAlign = Paint.Align.CENTER
                typeface = numeralTypeface
            }
            val inset = if (style.ticks == TickStyle.NONE) 0.78f else 0.66f
            // With quarter marks, only the quarter numerals: 12, 3, 6 and 9.
            val numbers = if (style.ticks == TickStyle.QUARTERS) listOf(3, 6, 9, 12) else (1..12).toList()
            for (n in numbers) {
                val a = Math.toRadians(n * 30.0)
                val x = cx + (r * inset * sin(a)).toFloat()
                val y = cy - (r * inset * cos(a)).toFloat() - (text.ascent() + text.descent()) / 2
                canvas.drawText(n.toString(), x, y, text)
            }
        }

        val hourAngle = ((hours % 12) + minutes / 60f + seconds / 3600f) * 30f
        val minuteAngle = (minutes + seconds / 60f) * 6f
        val secondAngle = seconds * 6f
        when (style.hands) {
            HandStyle.THICK -> {
                hand(canvas, paint, cx, cy, hourAngle, r * 0.5f, r * 0.085f, colors.hourHand)
                hand(canvas, paint, cx, cy, minuteAngle, r * 0.76f, r * 0.06f, colors.minuteHand)
            }
            HandStyle.THIN -> {
                hand(canvas, paint, cx, cy, hourAngle, r * 0.5f, r * 0.04f, colors.hourHand)
                hand(canvas, paint, cx, cy, minuteAngle, r * 0.76f, r * 0.025f, colors.minuteHand)
            }
            HandStyle.DOT -> {
                dot(canvas, paint, cx, cy, hourAngle, r * 0.48f, r * 0.075f, colors.hourHand)
                dot(canvas, paint, cx, cy, minuteAngle, r * 0.76f, r * 0.055f, colors.minuteHand)
            }
        }
        if (style.secondHand) {
            if (style.hands == HandStyle.DOT) dot(canvas, paint, cx, cy, secondAngle, r * 0.88f, r * 0.03f, colors.secondHand)
            else hand(canvas, paint, cx, cy, secondAngle, r * 0.84f, r * 0.012f, colors.secondHand, tail = r * 0.12f)
        }
        if (style.hands != HandStyle.DOT) {
            paint.style = Paint.Style.FILL
            paint.color = if (style.secondHand) colors.secondHand else colors.minuteHand
            canvas.drawCircle(cx, cy, r * 0.035f, paint)
        }
    }

    private fun hand(canvas: Canvas, paint: Paint, cx: Float, cy: Float, degrees: Float, length: Float, width: Float, color: Int, tail: Float = 0f) {
        val a = Math.toRadians(degrees.toDouble())
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = width
        paint.color = color
        canvas.drawLine(
            cx - (tail * sin(a)).toFloat(), cy + (tail * cos(a)).toFloat(),
            cx + (length * sin(a)).toFloat(), cy - (length * cos(a)).toFloat(), paint,
        )
    }

    private fun dot(canvas: Canvas, paint: Paint, cx: Float, cy: Float, degrees: Float, distance: Float, radius: Float, color: Int) {
        val a = Math.toRadians(degrees.toDouble())
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawCircle(cx + (distance * sin(a)).toFloat(), cy - (distance * cos(a)).toFloat(), radius, paint)
    }
}
