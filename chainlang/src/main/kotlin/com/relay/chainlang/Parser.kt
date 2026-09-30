package com.relay.chainlang

/**
 * Recursive-descent parser for the grammar in spec §6.4.
 *
 * Known scope limit (documented, not accidental): at the start of an element, `(` always starts
 * a nested group. Parenthesized arithmetic like `(3 + 4)` works only after an operator or inside
 * call arguments.
 */
internal class Parser(private val tokens: List<LexedToken>) {
    private var index = 0

    private val timeFns = setOf("MINUTE", "HOUR", "SECOND")

    /** Reserved words: never a block name. Kept in sync with [ChainLang.KEYWORDS]. */
    private val keywords = ChainLang.KEYWORDS

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
            is Token.DurationLiteral, is Token.Number -> parseDuration()
            is Token.Decimal -> throw ChainSyntaxException("decimals only work inside range(); use 1m30s for a step", lexed.pos)
            is Token.Str -> throw ChainSyntaxException("a label goes after a duration, like 5m \"stretch\"", lexed.pos)
            is Token.Ident -> parseIdentElement(tok.name, lexed.pos)
            else -> throw ChainSyntaxException("expected an element (duration, reference, or function)", lexed.pos)
        }
    }

    private fun parseIdentElement(name: String, pos: Int): ChainNode = when {
        name == "repeat" -> parseRepeatCall()
        name == "range" -> parseRangeCall()
        name == "arange" -> throw ChainSyntaxException("arange was removed: range now takes fractional steps", pos)
        name in timeFns || name == "RANDOM" -> parseDuration()
        name == "NOW" -> throw ChainSyntaxException("NOW can only appear inside MINUTE()/HOUR()/SECOND()", pos)
        else -> {
            if (tokens[index + 1].token is Token.LParen) throw ChainSyntaxException("unknown function \"$name\"", pos)
            val next = tokens[index + 1].token
            if (next is Token.Plus || next is Token.Minus) {
                parseDuration() // a name inside arithmetic: its total duration
            } else {
                advance()
                if (peek().token is Token.Str) {
                    throw ChainSyntaxException("labels go on anonymous steps; \"$name\" already has its own name", peek().pos)
                }
                Ref(name)
            }
        }
    }

    /** `term ((+|-) term)* [STRING]`, see [DurationNode]. */
    private fun parseDuration(): DurationNode {
        val terms = mutableListOf(SignedTerm(false, parseDurationTerm()))
        while (true) {
            when (peek().token) {
                is Token.Plus -> { advance(); terms += SignedTerm(false, parseDurationTerm()) }
                is Token.Minus -> { advance(); terms += SignedTerm(true, parseDurationTerm()) }
                else -> break
            }
        }
        val label = (peek().token as? Token.Str)?.let { advance(); it.value }
        return DurationNode(terms, label)
    }

    private fun parseDurationTerm(): DurationTerm {
        val lexed = peek()
        return when (val tok = lexed.token) {
            is Token.DurationLiteral -> { advance(); SecondsTerm(tok.seconds) }
            is Token.Number -> { advance(); MinutesTerm(IntLiteral(tok.value)) }
            is Token.Ident -> when {
                tok.name in timeFns || tok.name == "RANDOM" -> MinutesTerm(parseIdentIntTerm(tok.name, lexed.pos))
                tok.name in keywords -> throw ChainSyntaxException("\"${tok.name}\" can't be used in arithmetic", lexed.pos)
                else -> { advance(); RefValueTerm(tok.name) }
            }
            is Token.LParen -> { advance(); val e = parseIntExpr(); expect<Token.RParen>(); MinutesTerm(e) }
            else -> throw ChainSyntaxException("expected a duration, a number or a name", lexed.pos)
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

    private fun parseRangeCall(): ChainNode {
        expectIdent("range")
        expect<Token.LParen>()
        val start = parseIntExpr(allowDecimal = true)
        expectComma()
        val stop = parseIntExpr(allowDecimal = true)
        var step: IntExpr? = null
        if (peek().token is Token.Comma) {
            advance()
            step = parseIntExpr(allowDecimal = true)
        }
        expect<Token.RParen>()
        return RangeCall(start, stop, step)
    }

    private fun expectComma() {
        val lexed = advance()
        if (lexed.token !is Token.Comma) throw ChainSyntaxException("expected \",\", found ${lexed.token}", lexed.pos)
    }

    private fun parseIntExpr(allowDecimal: Boolean = false): IntExpr {
        var left = parseIntTerm(allowDecimal)
        while (true) {
            when (peek().token) {
                is Token.Plus -> { advance(); left = IntAdd(left, parseIntTerm(allowDecimal)) }
                is Token.Minus -> { advance(); left = IntSub(left, parseIntTerm(allowDecimal)) }
                else -> return left
            }
        }
    }

    private fun parseIntTerm(allowDecimal: Boolean = false): IntExpr {
        val lexed = peek()
        return when (val tok = lexed.token) {
            is Token.Number -> { advance(); IntLiteral(tok.value) }
            is Token.Decimal -> {
                if (!allowDecimal) throw ChainSyntaxException("decimals only work inside range()", lexed.pos)
                advance()
                DecimalLiteral(tok.value)
            }
            is Token.LParen -> { advance(); val e = parseIntExpr(allowDecimal); expect<Token.RParen>(); e }
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
