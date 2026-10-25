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
import com.relay.app.ui.theme.Accent
import com.relay.app.ui.theme.Brass
import com.relay.app.ui.theme.DarkTokens
import com.relay.app.ui.theme.LightTokens
import kotlin.math.min

/**
 * A widget's background, shape and border (spec §09), shared by every Relay widget. Each
 * widget's spec stores these fields itself, so saved widgets keep their JSON shape.
 */
data class WidgetFrame(
    val background: WidgetBackground = WidgetBackground.FOLLOW_THEME,
    val backgroundColor: Long? = null,
    val backgroundColor2: Long? = null,
    val corners: WidgetCorners = WidgetCorners.ROUNDED,
    val borderWidthDp: Int = 0,
    val borderColor: Long? = null,
)

/** The theme colours a widget draws with, light or dark as the system is. */
data class WidgetTheme(val surface: Int, val onSurface: Int, val muted: Int, val accent: Int) {
    companion object {
        fun of(context: Context): WidgetTheme {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val dark = night == Configuration.UI_MODE_NIGHT_YES
            return WidgetTheme(
                surface = (if (dark) DarkTokens.surfaceContainer else LightTokens.surfaceContainerLow).toArgb(),
                onSurface = (if (dark) DarkTokens.onSurface else LightTokens.onSurface).toArgb(),
                muted = (if (dark) DarkTokens.onSurfaceVariant else LightTokens.onSurfaceVariant).toArgb(),
                accent = (if (dark) DarkTokens.primary else LightTokens.primary).toArgb(),
            )
        }
    }
}

/** Draws a [WidgetFrame]: Glance can't draw gradients or borders, so this goes into a bitmap. */
object WidgetFrameRenderer {
    /** Keeps memory and the RemoteViews bitmap budget in check on big widgets. */
    const val MAX_SIDE_PX = 1200

    /** The corner radius of a ROUNDED widget, in dp. */
    const val ROUNDED_DP = 20f

    /** Pixels per dp for a widget of this size, scaled down so the bitmap stays within [maxSidePx]. */
    fun pxPerDp(context: Context, widthDp: Float, heightDp: Float, maxSidePx: Int = MAX_SIDE_PX): Float {
        val density = context.resources.displayMetrics.density
        return density * min(1f, maxSidePx / (maxOf(widthDp, heightDp) * density))
    }

    /** Just the frame, for a widget whose content is native Glance views drawn on top. */
    fun render(context: Context, frame: WidgetFrame, widthDp: Float, heightDp: Float, maxSidePx: Int = MAX_SIDE_PX): Bitmap {
        val px = pxPerDp(context, widthDp, heightDp, maxSidePx)
        val w = (widthDp * px).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, (heightDp * px).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), frame, bitmap.width, bitmap.height, px, WidgetTheme.of(context))
        return bitmap
    }

    fun draw(canvas: Canvas, frame: WidgetFrame, w: Int, h: Int, px: Float, theme: WidgetTheme) {
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val radius = when (frame.corners) {
            WidgetCorners.SHARP -> 0f
            WidgetCorners.ROUNDED -> ROUNDED_DP * px
            WidgetCorners.PILL -> min(w, h) / 2f
        }
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        when (frame.background) {
            WidgetBackground.TRANSPARENT -> {}
            WidgetBackground.FOLLOW_THEME -> { bg.color = theme.surface; canvas.drawRect(rect, bg) }
            WidgetBackground.SOLID -> { bg.color = frame.backgroundColor?.toInt() ?: theme.surface; canvas.drawRect(rect, bg) }
            WidgetBackground.GRADIENT -> {
                bg.shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    frame.backgroundColor?.toInt() ?: Accent.toArgb(), frame.backgroundColor2?.toInt() ?: Brass.toArgb(),
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRect(rect, bg)
            }
        }
        canvas.restore()
        if (frame.borderWidthDp > 0) drawBorder(canvas, frame, w, h, frame.borderWidthDp * px, radius, theme)
    }

    private fun drawBorder(canvas: Canvas, frame: WidgetFrame, w: Int, h: Int, stroke: Float, radius: Float, theme: WidgetTheme) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = frame.borderColor?.toInt() ?: theme.accent
        }
        val inset = RectF(stroke / 2, stroke / 2, w - stroke / 2, h - stroke / 2)
        canvas.drawRoundRect(inset, maxOf(0f, radius - stroke / 2), maxOf(0f, radius - stroke / 2), border)
    }
}
