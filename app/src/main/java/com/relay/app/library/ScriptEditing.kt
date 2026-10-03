package com.relay.app.library

/** Text plus a collapsed-or-not selection, independent of Compose so it can be unit-tested. */
data class EditState(val text: String, val selectionStart: Int, val selectionEnd: Int = selectionStart)

/**
 * The script editor's typing helpers (spec §05): auto-close `(` and `"`, type over a closing
 * character that's already there, auto-indent on Enter, and delete an empty `()` pair at once.
 * Only single-character edits at a collapsed cursor are touched; pastes and everything else pass
 * through unchanged, so the helpers never fight the user.
 */
object ScriptEditing {
    private const val INDENT = "  "

    fun transform(old: EditState, new: EditState): EditState {
        val collapsed = old.selectionStart == old.selectionEnd && new.selectionStart == new.selectionEnd
        if (!collapsed) return new
        val at = old.selectionStart
        return when {
            new.text.length == old.text.length + 1 && new.selectionStart == at + 1 &&
                new.text.regionMatches(0, old.text, 0, at) && new.text.regionMatches(at + 1, old.text, at, old.text.length - at) ->
                typed(old, new.text[at])
            new.text.length == old.text.length - 1 && new.selectionStart == at - 1 && at > 0 ->
                backspaced(old, new)
            else -> new
        }
    }

    private fun typed(old: EditState, c: Char): EditState {
        val at = old.selectionStart
        val text = old.text
        val next = text.getOrNull(at)
        // Comments are free text: no pairing there. Enter still keeps the indent.
        if (c != '\n' && ScriptRefs.inLabelOrComment(text, at) && c != '"') return insert(text, at, c.toString())
        if (c == '"' && isInComment(text, at)) return insert(text, at, c.toString())
        return when (c) {
            '(' -> insert(text, at, "()", cursorOffset = 1)
            ')' -> if (next == ')') EditState(text, at + 1) else insert(text, at, ")")
            '"' -> when {
                next == '"' -> EditState(text, at + 1)
                insideLabel(text, at) -> insert(text, at, "\"")
                else -> insert(text, at, "\"\"", cursorOffset = 1)
            }
            '\n' -> {
                val lineStart = text.lastIndexOf('\n', at - 1) + 1
                val base = text.substring(lineStart).takeWhile { it == ' ' || it == '\t' }
                val opened = text.getOrNull(at - 1) == '('
                val inner = if (opened) base + INDENT else base
                if (opened && next == ')') {
                    // "(|)" + Enter: the close paren goes on its own line at the base indent.
                    val inserted = "\n$inner\n$base"
                    EditState(text.substring(0, at) + inserted + text.substring(at), at + 1 + inner.length)
                } else {
                    insert(text, at, "\n$inner")
                }
            }
            else -> insert(text, at, c.toString())
        }
    }

    /** Backspace right between an empty pair removes both halves. */
    private fun backspaced(old: EditState, new: EditState): EditState {
        val at = old.selectionStart
        val removed = old.text[at - 1]
        val after = old.text.getOrNull(at)
        val pair = (removed == '(' && after == ')') || (removed == '"' && after == '"')
        if (!pair || !new.text.regionMatches(0, old.text, 0, at - 1)) return new
        return EditState(old.text.removeRange(at - 1, at + 1), at - 1)
    }

    private fun insert(text: String, at: Int, s: String, cursorOffset: Int = s.length) =
        EditState(text.substring(0, at) + s + text.substring(at), at + cursorOffset)

    private fun insideLabel(text: String, at: Int) = text.substring(text.lastIndexOf('\n', at - 1) + 1, at).count { it == '"' } % 2 == 1

    private fun isInComment(text: String, at: Int) = ScriptRefs.inLabelOrComment(text, at) && !insideLabel(text, at)

    /** Replaces the word starting at [start] (up to the cursor) with [replacement]. */
    fun complete(state: EditState, start: Int, replacement: String): EditState {
        val at = state.selectionStart
        return EditState(state.text.substring(0, start) + replacement + state.text.substring(at), start + replacement.length)
    }
}
