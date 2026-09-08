package com.clockr.chainlang

/**
 * Turns a chain expression (spec §6.4) into a flat token stream.
 *
 * The one non-obvious bit: `2h30m10s` is lexed as a single [Token.DurationLiteral] (seconds
 * already summed), while a bare `120` is a [Token.Number] — the "bare number ⇒ minutes" rule
 * from the spec is a parser/evaluator concern, not a lexer one, since a bare number also shows
 * up as a plain integer inside `range(...)`/`repeat(...)` arguments where it must NOT be scaled.
 */
internal class Lexer(private val input: String) {
    private var pos = 0

    fun tokenize(): List<Pair<Token, Int>> {
        val tokens = mutableListOf<Pair<Token, Int>>()
        while (true) {
            skipWhitespace()
            val start = pos
            if (pos >= input.length) {
                tokens += Token.Eof to start
                break
            }
            val c = input[pos]
            when {
                c == '(' -> { pos++; tokens += Token.LParen to start }
                c == ')' -> { pos++; tokens += Token.RParen to start }
                c == ',' -> { pos++; tokens += Token.Comma to start }
                c == '+' -> { pos++; tokens += Token.Plus to start }
                c == '-' -> { pos++; tokens += Token.Minus to start }
                c.isDigit() -> tokens += lexNumberOrDuration() to start
                c.isLetter() -> tokens += lexIdent() to start
                else -> throw ChainSyntaxException("unexpected character '$c'", start)
            }
        }
        return tokens
    }

    private fun skipWhitespace() {
        while (pos < input.length && input[pos].isWhitespace()) pos++
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
