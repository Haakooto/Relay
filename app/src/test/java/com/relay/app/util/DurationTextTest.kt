package com.relay.app.util

import com.relay.chainlang.ChainLang
import com.relay.chainlang.NowClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DurationTextTest {
    @Test fun exprIsCompactRelayLanguage() {
        assertEquals("0s", DurationText.expr(0))
        assertEquals("45s", DurationText.expr(45))
        assertEquals("25m", DurationText.expr(1500))
        assertEquals("1h5m30s", DurationText.expr(3930))
        assertEquals("2h", DurationText.expr(7200))
    }

    @Test fun exprParsesBackToTheSameSeconds() {
        for (seconds in listOf(1L, 59, 60, 61, 3599, 3600, 3661, 86_400)) {
            assertEquals(seconds, ChainLang.evalSingleDuration(DurationText.expr(seconds), NowClock(12, 0, 0)))
        }
    }

    @Test fun plainIsWhatTheKeypadTypes() {
        assertTrue(DurationText.isPlain("25"))
        assertTrue(DurationText.isPlain("1h30m"))
        assertFalse(DurationText.isPlain(""))
        assertFalse(DurationText.isPlain("RANDOM(5, 10)"))
        assertFalse(DurationText.isPlain("5m + 1m"))
    }

    /** What +1m does to an expression the keypad can't rewrite. */
    @Test fun quickAddAppendsToAnExpression() {
        assertEquals(21 * 60L, ChainLang.evalSingleDuration("MINUTE(NOW) + 1m", NowClock(12, 20, 0)))
    }
}
