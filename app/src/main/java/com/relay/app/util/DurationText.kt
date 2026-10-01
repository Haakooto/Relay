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

    /** Stopwatch style with hundredths: "1:02:03.45", "02:03.45". */
    fun stopwatch(millis: Long): String {
        val ms = millis.coerceAtLeast(0)
        val h = ms / 3_600_000
        val m = (ms / 60_000) % 60
        val s = (ms / 1000) % 60
        val cs = (ms / 10) % 100
        return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, cs) else "%02d:%02d.%02d".format(m, s, cs)
    }
}
