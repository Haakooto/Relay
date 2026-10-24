package com.relay.app.util

/** Countdown and duration text. */
object DurationText {
    /** "1:05:09", "5:09", "0:09". */
    fun clock(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** "2h 5m", "25m", "1m 30s", "45s": for totals in lists. */
    fun short(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return buildList {
            if (h > 0) add("${h}h")
            if (m > 0) add("${m}m")
            if (s > 0 || (h == 0L && m == 0L)) add("${s}s")
        }.joinToString(" ")
    }

    /** The relay language's own spelling, no spaces: "1h5m30s", "25m", "45s", "0s". */
    fun expr(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        if (s == 0L) return "0s"
        return listOf(s / 3600 to "h", (s % 3600) / 60 to "m", s % 60 to "s").filter { it.first > 0 }.joinToString("") { "${it.first}${it.second}" }
    }

    /** Only digits and units, what the keypad can type: "25", "1h30m". Not "RANDOM(5, 10)". */
    fun isPlain(expr: String): Boolean = expr.isNotEmpty() && expr.all { it.isDigit() || it in "hms" }

    /** Stopwatch style with hundredths: "1:02:03.45", "02:03.45". */
    fun stopwatch(millis: Long): String {
        val ms = millis.coerceAtLeast(0)
        val h = ms / 3_600_000
        val m = (ms / 60_000) % 60
        val s = (ms / 1000) % 60
        val cs = (ms / 10) % 100
        return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, cs) else "%02d:%02d.%02d".format(m, s, cs)
    }

    /** Like [stopwatch] but keeps a minus sign: "-00:05.00". No "-00:00.00" in the last 10 ms. */
    fun signedStopwatch(millis: Long): String = (if (millis <= -10) "-" else "") + stopwatch(kotlin.math.abs(millis))
}
