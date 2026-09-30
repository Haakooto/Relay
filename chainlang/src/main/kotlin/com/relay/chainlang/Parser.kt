package com.relay.chainlang

/**
 * Recursive-descent parser for the grammar in spec §6.4.
 *
 * Known scope limit (documented, not accidental): a bare parenthesized arithmetic group like
 * `(3 + 4)` is only supported where an [IntExpr] is already expected (call arguments, the `NOW`
 * offset) — at the top of an "element", `(` always starts a nested chain-content group instead,
 * since that's the shape every real example in the spec actually uses.
 */
internal class Parser(private val tokens: List<LexedToken>) {
    private var index = 0

    private val timeFns = setOf("MINUTE", "HOUR", "SECOND")

    companion object {
        fun parse(source: String): Group {
            val tokens = Lexer(source).tokenize()
            val parser = Parser(tokens)
            val group = parser.parseChainContent()
            parser.expectEof()
            return group
        }
    }

    private fun peek(): LexedToken = tokens[index]

    private fun advance(): LexedToken = tokens[index].also { index++ }

    private inline fun <reified T : Token> expect(): T {
        val lexed = advance()
        return lexed.token as? T ?: throw ChainSyntaxException("expected ${T::class.simpleName}, found ${lexed.token}", lexed.pos)
    }

    private fun expectIdent(name: String) {
        val lexed = advance()
        if (lexed.token !is Token.Ident || lexed.token.name != name) {
            throw ChainSyntaxException("expected \"$name\", found ${lexed.token}", lexed.pos)
        }
    }

    private fun expectEof() {
        val lexed = peek()
        if (lexed.token !is Token.Eof) throw ChainSyntaxException("unexpected trailing input: ${lexed.token}", lexed.pos)
    }

    /**
     * Newlines and commas are interchangeable separators — a mix of both is fine, so is either
     * one repeated (blank lines, a trailing comma before a newline, etc). At least one of them
     * is still required between elements: bare whitespace alone is not a separator. A newline
     * is never a real [Token] (see [Lexer]) — [LexedToken.precededByNewline] is what's checked.
     */
    fun parseChainContent(): Group {
        expect<Token.LParen>()
        val elements = mutableListOf<ChainNode>()
        if (peek().token !is Token.RParen) {
            elements += parseElement()
            while (true) {
                val next = peek()
                if (next.token is Token.RParen) break
                val isComma = next.token is Token.Comma
                if (!isComma && !next.precededByNewline) {
                    throw ChainSyntaxException("expected \",\" or a newline, found ${next.token}", next.pos)
                }
                if (isComma) advance()
                if (peek().token is Token.RParen) break // trailing separator before the close paren
                elements += parseElement()
            }
        }
        expect<Token.RParen>()
        return Group(elements)
    }

    private fun parseElement(): ChainNode {
        val lexed = peek()
        return when (val tok = lexed.token) {
            is Token.LParen -> parseChainContent()
            is Token.DurationLiteral -> { advance(); DurationLiteral(tok.seconds) }
            is Token.Number -> DurationExpr(parseIntExpr())
            is Token.Ident -> parseIdentElement(tok.name, lexed.pos)
            else -> throw ChainSyntaxException("expected an element (duration, reference, or function)", lexed.pos)
        }
    }

    private fun parseIdentElement(name: String, pos: Int): ChainNode = when {
        name == "repeat" -> parseRepeatCall()
        name == "range" -> parseRangeLikeCall("range") { s, e, st -> RangeCall(s, e, st) }
        name == "arange" -> parseRangeLikeCall("arange") { s, e, st -> ArangeCall(s, e, st) }
        name in timeFns || name == "RANDOM" -> DurationExpr(parseIntExpr())
        name == "NOW" -> throw ChainSyntaxException("NOW can only appear inside MINUTE()/HOUR()/SECOND()", pos)
        else -> {
            if (peek().token is Token.LParen) throw ChainSyntaxException("unknown function \"$name\"", pos)
            advance()
            Ref(name)
        }
    }

    private fun parseRepeatCall(): RepeatCall {
        expectIdent("repeat")
        expect<Token.LParen>()
        val count = parseIntExpr()
        expectComma()
        val body = parseElement()
        expect<Token.RParen>()
        return RepeatCall(count, body)
    }

    private fun parseRangeLikeCall(name: String, build: (IntExpr, IntExpr, IntExpr?) -> ChainNode): ChainNode {
        expectIdent(name)
        expect<Token.LParen>()
        val start = parseIntExpr()
        expectComma()
        val stop = parseIntExpr()
        var step: IntExpr? = null
        if (peek().token is Token.Comma) {
            advance()
            step = parseIntExpr()
        }
        expect<Token.RParen>()
        return build(start, stop, step)
    }

    private fun expectComma() {
        val lexed = advance()
        if (lexed.token !is Token.Comma) throw ChainSyntaxException("expected \",\", found ${lexed.token}", lexed.pos)
    }

    private fun parseIntExpr(): IntExpr {
        var left = parseIntTerm()
        while (true) {
            when (peek().token) {
                is Token.Plus -> { advance(); left = IntAdd(left, parseIntTerm()) }
                is Token.Minus -> { advance(); left = IntSub(left, parseIntTerm()) }
                else -> return left
            }
        }
    }

    private fun parseIntTerm(): IntExpr {
        val lexed = peek()
        return when (val tok = lexed.token) {
            is Token.Number -> { advance(); IntLiteral(tok.value) }
            is Token.LParen -> { advance(); val e = parseIntExpr(); expect<Token.RParen>(); e }
            is Token.Ident -> parseIdentIntTerm(tok.name, lexed.pos)
            else -> throw ChainSyntaxException("expected a number or expression", lexed.pos)
        }
    }

    private fun parseIdentIntTerm(name: String, pos: Int): IntExpr = when {
        name in timeFns -> {
            advance()
            expect<Token.LParen>()
            val time = parseTimeExpr()
            expect<Token.RParen>()
            TimeFunc(TimeUnit.valueOf(name), time)
        }
        name == "RANDOM" -> {
            advance()
            expect<Token.LParen>()
            val lo = parseIntExpr()
            expectComma()
            val hi = parseIntExpr()
            expect<Token.RParen>()
            RandomCall(lo, hi)
        }
        else -> throw ChainSyntaxException("unexpected identifier \"$name\" in an expression", pos)
    }

    private fun parseTimeExpr(): TimeExpr {
        val lexed = peek()
        if (lexed.token !is Token.Ident || lexed.token.name != "NOW") {
            throw ChainSyntaxException("expected NOW", lexed.pos)
        }
        advance()
        var offset = 0L
        when (peek().token) {
            is Token.Plus -> { advance(); offset = readOffsetSeconds() }
            is Token.Minus -> { advance(); offset = -readOffsetSeconds() }
            else -> {}
        }
        return TimeExpr(offset)
    }

    /** Bare-number-⇒-minutes applies here too, in addition to explicit `2h30m` literals. */
    private fun readOffsetSeconds(): Long {
        val lexed = advance()
        return when (val tok = lexed.token) {
            is Token.DurationLiteral -> tok.seconds
            is Token.Number -> tok.value * 60
            else -> throw ChainSyntaxException("expected a duration after +/-", lexed.pos)
        }
    }
}
