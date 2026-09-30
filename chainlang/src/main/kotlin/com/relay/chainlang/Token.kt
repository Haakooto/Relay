package com.relay.chainlang

internal sealed interface Token {
    data object LParen : Token
    data object RParen : Token
    data object Comma : Token
    data object Plus : Token
    data object Minus : Token
    data class Number(val value: Long) : Token
    /** `0.5`: only meaningful as a `range(...)` argument; the parser rejects it anywhere else. */
    data class Decimal(val value: Double) : Token
    /** `"stretch"`: a display label on an anonymous step. */
    data class Str(val value: String) : Token
    /** A compound literal like `2h30m10s`, already reduced to total seconds. */
    data class DurationLiteral(val seconds: Long) : Token
    data class Ident(val name: String) : Token
    data object Eof : Token
}
