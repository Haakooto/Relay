package com.relay.app.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Norwegian is Nynorsk only. Android doesn't fall back from a bokmål phone (nb) to nn, so
 * values-nb carries an exact copy of the Nynorsk strings. This keeps the copy honest.
 */
class NorwegianStringsTest {
    @Test fun bokmaalFolderIsAnExactCopyOfNynorsk() {
        val res = File("src/main/res")
        assertEquals(File(res, "values-nn/strings.xml").readText(), File(res, "values-nb/strings.xml").readText())
    }
}
