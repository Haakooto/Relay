package com.clockr.app.ui.clock

import com.clockr.app.data.ClockAnalogueVariant
import java.time.LocalTime

/** Hand angles in degrees clockwise from 12 o'clock — pure geometry, no drawing API, so both the
 *  in-app Compose `Canvas` face (ClockScreen) and the widget's `android.graphics.Canvas` bitmap
 *  renderer (AnalogueClockRenderer) compute identical hand positions from the same time. */
data class ClockHandAngles(val hourDeg: Float, val minuteDeg: Float, val secondDeg: Float)

fun clockHandAngles(time: LocalTime): ClockHandAngles {
    val secondDeg = time.second * 6f
    val minuteDeg = time.minute * 6f + time.second * 0.1f
    val hourDeg = (time.hour % 12) * 30f + time.minute * 0.5f
    return ClockHandAngles(hourDeg, minuteDeg, secondDeg)
}

/** Per-variant look, shared by both renderers so "Sleek" (say) means the same thing on the
 *  widget as it does in-app. Proportions are fractions of the face radius. */
data class AnalogueClockSpec(
    val showNumerals: Boolean,
    val tickCount: Int,
    val majorTickEvery: Int,
    val hourHandLength: Float,
    val minuteHandLength: Float,
    val handWidthScale: Float,
    val showCenterDot: Boolean,
    val showFaceRing: Boolean,
) {
    companion object {
        fun forVariant(variant: ClockAnalogueVariant): AnalogueClockSpec = when (variant) {
            ClockAnalogueVariant.MINIMAL -> AnalogueClockSpec(
                showNumerals = false, tickCount = 4, majorTickEvery = 1,
                hourHandLength = 0.5f, minuteHandLength = 0.75f,
                handWidthScale = 0.7f, showCenterDot = false, showFaceRing = false,
            )
            ClockAnalogueVariant.SLEEK -> AnalogueClockSpec(
                showNumerals = false, tickCount = 12, majorTickEvery = 3,
                hourHandLength = 0.55f, minuteHandLength = 0.8f,
                handWidthScale = 0.8f, showCenterDot = true, showFaceRing = true,
            )
            ClockAnalogueVariant.CLASSIC -> AnalogueClockSpec(
                showNumerals = true, tickCount = 12, majorTickEvery = 3,
                hourHandLength = 0.55f, minuteHandLength = 0.82f,
                handWidthScale = 1f, showCenterDot = true, showFaceRing = true,
            )
            ClockAnalogueVariant.TECHY -> AnalogueClockSpec(
                showNumerals = false, tickCount = 60, majorTickEvery = 5,
                hourHandLength = 0.5f, minuteHandLength = 0.85f,
                handWidthScale = 0.6f, showCenterDot = true, showFaceRing = true,
            )
        }
    }
}
