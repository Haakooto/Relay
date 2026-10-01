package com.relay.app.clock

import com.relay.app.util.AppLocale
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Time and date text for a [ClockStyle], shared by the app and widgets. */
object ClockText {
    fun time(t: ZonedDateTime, style: ClockStyle, is24hSetting: Boolean, withSeconds: Boolean = style.showSeconds): String {
        val is24h = style.is24h(is24hSetting)
        val pattern = buildString {
            append(if (is24h) "HH:mm" else "h:mm")
            if (withSeconds) append(":ss")
        }
        return t.format(DateTimeFormatter.ofPattern(pattern, AppLocale.formatting))
    }

    /** "pm" for 12-hour clocks, shown smaller beside the digits; null for 24-hour ones. */
    fun amPm(t: ZonedDateTime, style: ClockStyle, is24hSetting: Boolean): String? {
        if (style.is24h(is24hSetting) || !ClockFonts.supportsLetters(style.font)) return null
        return t.format(DateTimeFormatter.ofPattern("a", AppLocale.formatting))
    }

    fun date(t: ZonedDateTime, style: DateStyle): String? = when (style) {
        DateStyle.OFF -> null
        DateStyle.SHORT -> t.format(DateTimeFormatter.ofPattern("EEE d MMM", AppLocale.formatting))
        DateStyle.LONG -> t.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", AppLocale.formatting))
    }
}
