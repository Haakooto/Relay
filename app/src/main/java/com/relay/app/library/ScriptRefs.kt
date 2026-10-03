package com.relay.app.library

/** Text-level helpers over relay scripts that must respect quoted labels and `#` comments. Pure, unit-tested. */
object ScriptRefs {
    /** Replaces every reference to [old] with [new]; words in "quoted labels" and comments are left alone. */
    fun rename(script: String, old: String, new: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < script.length) {
            val c = script[i]
            when {
                c == '#' -> {
                    val end = script.indexOf('\n', i).let { if (it < 0) script.length else it }
                    out.append(script, i, end)
                    i = end
                }
                c == '"' -> {
                    val end = script.indexOf('"', i + 1).let { if (it < 0) script.length else it + 1 }
                    out.append(script, i, end)
                    i = end
                }
                c.isLetter() -> {
                    var j = i
                    while (j < script.length && (script[j].isLetterOrDigit() || script[j] == '_')) j++
                    val word = script.substring(i, j)
                    out.append(if (word == old) new else word)
                    i = j
                }
                c.isDigit() -> {
                    // Keep "25m", "2h30m" intact: a number and its unit letters are one token.
                    var j = i
                    while (j < script.length && (script[j].isLetterOrDigit() || script[j] == '.')) j++
                    out.append(script, i, j)
                    i = j
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    /**
     * The identifier being typed at [cursor], as (start, prefix), or null when the cursor isn't
     * right after one (or is inside a quoted label). Drives autocomplete.
     */
    fun wordAt(script: String, cursor: Int): Pair<Int, String>? {
        if (cursor <= 0 || cursor > script.length) return null
        if (inLabelOrComment(script, cursor)) return null
        var start = cursor
        while (start > 0 && (script[start - 1].isLetterOrDigit() || script[start - 1] == '_')) start--
        if (start == cursor || !script[start].isLetter()) return null
        return start to script.substring(start, cursor)
    }

    /** Whether [at] is inside a "quoted label" or after a `#` on its line (outside a label). */
    fun inLabelOrComment(script: String, at: Int): Boolean {
        var inLabel = false
        val lineStart = script.lastIndexOf('\n', at - 1) + 1
        for (i in 0 until at) {
            val c = script[i]
            if (c == '"') inLabel = !inLabel
            if (c == '\n') inLabel = false
            if (c == '#' && !inLabel && i >= lineStart) return true
        }
        return inLabel
    }
}
