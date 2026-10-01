package com.relay.app.clock

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import com.relay.app.R

/** One mapping from [DigitalFont] to real faces, for Compose and for bitmap (widget) drawing. */
object ClockFonts {
    fun compose(font: DigitalFont): FontFamily = when (font) {
        DigitalFont.SYSTEM -> FontFamily.Default
        DigitalFont.SERIF -> FontFamily.Serif
        DigitalFont.MONO -> FontFamily.Monospace
        DigitalFont.SEVEN_SEGMENT -> SevenSegment
        DigitalFont.DISPLAY -> Display
    }

    fun typeface(context: Context, font: DigitalFont): Typeface = when (font) {
        DigitalFont.SYSTEM -> Typeface.DEFAULT
        DigitalFont.SERIF -> Typeface.SERIF
        DigitalFont.MONO -> Typeface.MONOSPACE
        DigitalFont.SEVEN_SEGMENT -> ResourcesCompat.getFont(context, R.font.dseg7_classic_bold) ?: Typeface.MONOSPACE
        DigitalFont.DISPLAY -> ResourcesCompat.getFont(context, R.font.fraunces) ?: Typeface.SERIF
    }

    /** 7-segment digits have no lowercase or am/pm glyphs worth showing. */
    fun supportsLetters(font: DigitalFont) = font != DigitalFont.SEVEN_SEGMENT

    private val SevenSegment = FontFamily(Font(R.font.dseg7_classic_bold))
    /** Fraunces is a variable font: ask for real weights rather than synthesised bold. */
    @OptIn(ExperimentalTextApi::class)
    private val Display = FontFamily(
        Font(R.font.fraunces, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.fraunces, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    )
}
