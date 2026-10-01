package com.relay.app.util

import java.util.Locale

/**
 * The locale for dates, times and day names. Relay's English is British English only (not
 * configurable), so any English locale formats as en-GB: a phone set to US English still gets
 * "1 Oct 2026" and "Mon", never "Oct 1, 2026". Norwegian is Nynorsk only, so a bokmål phone
 * formats day and month names as Nynorsk too, matching the strings.
 */
object AppLocale {
    private val NYNORSK = Locale.forLanguageTag("nn-NO")

    val formatting: Locale
        get() = Locale.getDefault().let {
            when (it.language) {
                "en" -> Locale.UK
                "nn", "nb", "no" -> NYNORSK
                else -> it
            }
        }
}
