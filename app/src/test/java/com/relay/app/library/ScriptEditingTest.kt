package com.relay.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScriptEditingTest {
    /** Simulates typing [c] at the cursor marked | in [before]. */
    private fun type(before: String, c: Char): String {
        val at = before.indexOf('|')
        val text = before.replace("|", "")
        val new = EditState(text.substring(0, at) + c + text.substring(at), at + 1)
        val out = ScriptEditing.transform(EditState(text, at), new)
        return out.text.substring(0, out.selectionStart) + "|" + out.text.substring(out.selectionStart)
    }

    private fun backspace(before: String): String {
        val at = before.indexOf('|')
        val text = before.replace("|", "")
        val new = EditState(text.removeRange(at - 1, at), at - 1)
        val out = ScriptEditing.transform(EditState(text, at), new)
        return out.text.substring(0, out.selectionStart) + "|" + out.text.substring(out.selectionStart)
    }

    @Test fun openParenAutoCloses() = assertEquals("(|)", type("|", '('))
    @Test fun closeParenTypesOver() = assertEquals("(Work)|", type("(Work|)", ')'))
    @Test fun closeParenInsertsWhenNoneAhead() = assertEquals("(a))|", type("(a)|", ')'))
    @Test fun quoteAutoCloses() = assertEquals("(5m \"|\")", type("(5m |)", '"'))
    @Test fun quoteTypesOver() = assertEquals("(5m \"x\"|)", type("(5m \"x|\")", '"'))
    @Test fun enterInsideEmptyParensIndents() = assertEquals("(\n  |\n)", type("(|)", '\n'))
    @Test fun enterKeepsIndent() = assertEquals("(\n  Work,\n  |\n)", type("(\n  Work,|\n)", '\n'))
    @Test fun enterAfterNestedOpenIndentsMore() = assertEquals("(\n  repeat(4, (\n    |\n  ))", type("(\n  repeat(4, (|))", '\n').let { it })
    @Test fun backspaceRemovesEmptyPair() = assertEquals("|", backspace("(|)"))
    @Test fun backspaceNormalOtherwise() = assertEquals("(Wor|)", backspace("(Work|)"))

    @Test fun completeReplacesPrefix() {
        val out = ScriptEditing.complete(EditState("(Wo)", 3), start = 1, replacement = "Work")
        assertEquals(EditState("(Work)", 5), out)
    }

    @Test fun wordAtFindsIdentifier() {
        assertEquals(1 to "Wo", ScriptRefs.wordAt("(Wo)", 3))
        assertNull(ScriptRefs.wordAt("(25m)", 4)) // "m" belongs to a number
        assertNull(ScriptRefs.wordAt("(5m \"str", 8)) // inside a label
    }

    @Test fun renameIsTokenAware() {
        assertEquals("(Focus, Workday, 25m \"Work\", Focus - 2)", ScriptRefs.rename("(Work, Workday, 25m \"Work\", Work - 2)", "Work", "Focus"))
    }
}
