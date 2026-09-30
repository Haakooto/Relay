package com.relay.chainlang

/** A lexed token plus whether at least one newline appeared in the whitespace right before it. */
internal data class LexedToken(val token: Token, val pos: Int, val precededByNewline: Boolean)

/**
 * Turns a chain expression (spec §6.4) into a flat token stream.
 *
 * The one non-obvious bit: `2h30m10s` is lexed as a single [Token.DurationLiteral] (seconds
 * already summed), while a bare `120` is a [Token.Number] — the "bare number ⇒ minutes" rule
 * from the spec is a parser/evaluator concern, not a lexer one, since a bare number also shows
 * up as a plain integer inside `range(...)`/`repeat(...)` arguments where it must NOT be scaled.
 *
 * Newlines are otherwise-ordinary whitespace everywhere in the grammar — `repeat(4,\n (Work,
 * Break)\n)` parses exactly like the one-line form — except as an element separator inside
 * `chain_content`, where one is exactly as good as a comma. Rather than a real `Newline` token
 * (which would force every other grammar rule to explicitly tolerate it too), each token just
 * carries whether a newline preceded it; only [Parser.parseChainContent] looks at that flag.
 */
internal class Lexer(private val input: String) {
    private var pos = 0

    fun tokenize(): List<LexedToken> {
        val tokens = mutableListOf<LexedToken>()
        while (true) {
            val precededByNewline = skipWhitespace()
            val start = pos
            if (pos >= input.length) {
                tokens += LexedToken(Token.Eof, start, precededByNewline)
                break
            }
            val c = input[pos]
            val token = when {
                c == '(' -> { pos++; Token.LParen }
                c == ')' -> { pos++; Token.RParen }
                c == ',' -> { pos++; Token.Comma }
                c == '+' -> { pos++; Token.Plus }
                c == '-' -> { pos++; Token.Minus }
                c.isDigit() -> lexNumberOrDuration()
                c.isLetter() -> lexIdent()
                else -> throw ChainSyntaxException("unexpected character '$c'", start)
            }
            tokens += LexedToken(token, start, precededByNewline)
        }
        return tokens
    }

    /** @return whether a newline was among the whitespace skipped. */
    private fun skipWhitespace(): Boolean {
        var sawNewline = false
        while (pos < input.length && input[pos].isWhitespace()) {
            if (input[pos] == '\n') sawNewline = true
            pos++
        }
        return sawNewline
    }

    private fun readDigits(): Long {
        val start = pos
        while (pos < input.length && input[pos].isDigit()) pos++
        return input.substring(start, pos).toLong()
    }

    private fun lexNumberOrDuration(): Token {
        val first = readDigits()
        val unit = if (pos < input.length) input[pos] else null
        if (unit != 'h' && unit != 'm' && unit != 's') {
            return Token.Number(first)
        }

        var seconds = 0L
        var sawUnit = false
        val order = listOf('h', 'm', 's')
        var orderIdx = 0
        var pendingValue: Long? = first

        while (pendingValue != null) {
            val u = if (pos < input.length) input[pos] else null
            val idx = order.indexOf(u).takeIf { it >= 0 && it >= orderIdx }
                ?: throw ChainSyntaxException("expected one of h/m/s after $pendingValue (in order)", pos)
            pos++
            seconds += pendingValue * when (order[idx]) {
                'h' -> 3600L
                'm' -> 60L
                else -> 1L
            }
            sawUnit = true
            orderIdx = idx + 1
            pendingValue = if (pos < input.length && input[pos].isDigit() && orderIdx < order.size) {
                readDigits()
            } else {
                null
            }
        }
        check(sawUnit)
        return Token.DurationLiteral(seconds)
    }

    private fun lexIdent(): Token {
        val start = pos
        while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_')) pos++
        return Token.Ident(input.substring(start, pos))
    }
}
