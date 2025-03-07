package kakkoiichris.kb.lexer

import kakkoiichris.kb.runtime.Empty
import kakkoiichris.kb.util.KBError
import kakkoiichris.kb.util.Source
import java.util.*

class Lexer(private val source: Source) : Iterator<Token> {
    companion object {
        const val NUL = '\u0000'

        val keywords = Token.Type
            .values()
            .filter { it.symbol.all(Char::isLetter) }
            .associateBy(Token.Type::symbol)

        val literals = listOf(true, false, Empty)
            .associateBy(Objects::toString)

        val longRegex = """\d+[Ll]""".toRegex()
        val intRegex = """\d+""".toRegex()
        val floatRegex = """\d+(\.\d+)?([Ee][+-]?\d+)?[Ff]""".toRegex()
        val doubleRegex = """\d+(\.\d+)?([Ee][+-]?\d+)?[Dd]?""".toRegex()
        val longBinaryRegex = """0b[01]+[Ll]""".toRegex()
        val longHexRegex = """0x[\dA-Fa-f]+[Ll]""".toRegex()
        val intBinaryRegex = """0b[01]+""".toRegex()
        val intHexRegex = """0x[\dA-Fa-f]+""".toRegex()
    }

    private val lines = source.content.split("\n")

    private var pos = 0
    private var row = 1
    private var col = 1

    override fun hasNext() = pos <= source.content.length

    override fun next(): Token {
        while (!atEndOfFile()) {
            if (match { isWhitespace() }) {
                skipWhitespace()

                continue
            }

            if (match("rem")) {
                skipLineComment()

                continue
            }

            return when {
                match { isDigit() }  -> number()

                match { isLetter() } -> word()

                match('\'')          -> char()

                match('"')           -> string()

                else                 -> symbol()
            }
        }

        return endOfFile()
    }

    private fun peek(offset: Int = 0) =
        if (pos + offset in source.content.indices)
            source.content[pos + offset]
        else
            NUL

    private fun look(length: Int) =
        buildString {
            repeat(length) { offset ->
                append(peek(offset))
            }
        }

    private fun here() =
        Location(source.name, row, col)
    
    private fun getLine(location: Location=here())=
        lines[location.row-1]

    private fun step(count: Int = 1) {
        repeat(count) {
            if (match('\n')) {
                row++
                col = 1
            }
            else {
                col++
            }

            pos++
        }
    }

    private fun match(char: Char) =
        peek() == char

    private fun match(predicate: Char.() -> Boolean) =
        peek().predicate()

    private fun match(string: String) =
        look(string.length).equals(string, ignoreCase = true)

    private fun skip(char: Char): Boolean {
        if (match(char)) {
            step()

            return true
        }

        return false
    }

    private fun skip(predicate: Char.() -> Boolean): Boolean {
        if (match(predicate)) {
            step()

            return true
        }

        return false
    }

    private fun skip(string: String): Boolean {
        if (match(string)) {
            step(string.length)

            return true
        }

        return false
    }

    private fun mustSkip(char: Char) {
        if (!skip(char)) {
            val context = Context(here(), col..(col + 1), getLine())

            KBError.invalidCharacter(peek(), char, context)
        }
    }

    @Suppress("SameParameterValue")
    private fun mustSkip(string: String) {
        if (!skip(string)) {
            val context = Context(here(), col..(col + string.length), getLine())

            KBError.invalidSequence(look(string.length), string, context)
        }
    }

    private fun atEndOfFile() =
        match(NUL)

    private fun skipWhitespace() {
        while (skip { isWhitespace() }) Unit
    }

    private fun skipLineComment() {
        mustSkip("rem")

        while (!atEndOfFile() && !skip('\n')) {
            step()
        }
    }

    private fun StringBuilder.take() {
        append(peek())

        step()
    }

    private fun number(): Token {
        val location = here()

        val start = col

        val result = buildString {
            do {
                take()
            }
            while (match { isDigit() })

            when {
                match('b') -> {
                    do {
                        take()
                    }
                    while (match { this in "01" })

                    if (match { this in "Ll" }) {
                        take()
                    }
                }

                match('x') -> {
                    do {
                        take()
                    }
                    while (match { this in "0123456789ABCDEFabcdef" })

                    if (match { this in "Ll" }) {
                        take()
                    }
                }

                match('.') -> {
                    do {
                        take()
                    }
                    while (match { isDigit() })

                    if (match { this in "Ee" }) {
                        take()

                        do {
                            take()
                        }
                        while (match { isDigit() })
                    }

                    if (match { this in "FfDd" }) {
                        take()
                    }
                }

                else       -> {
                    if (match { this in "FfDdLl" }) {
                        take()
                    }
                }
            }

            if (match { this in "Ee" }) {
                take()

                do {
                    take()
                }
                while (match { isDigit() })
            }

            if (match { this in "FfDd" }) {
                take()
            }
        }

        val region = start..col

        val number = when {
            result.matches(longRegex)       -> result.dropLast(1).toLong()

            result.matches(intRegex)        -> result.toInt()

            result.matches(floatRegex)      -> result.dropLast(1).toFloat()

            result.matches(doubleRegex)     -> result.toDouble()

            result.matches(longBinaryRegex) -> result.substring(2, result.length - 1).toInt(2)

            result.matches(longHexRegex)    -> result.substring(2, result.length - 1).toInt(16)

            result.matches(intBinaryRegex)  -> result.substring(2).toInt(2)

            result.matches(intHexRegex)     -> result.substring(2).toInt(16)

            else                            -> KBError.failure("UNEXPECTED NUMBER FORMAT")
        }

        val context = Context(location, region, getLine(location))

        return Token(context, Token.Type.VALUE, number)
    }

    private fun word(): Token {
        val location = here()

        val start = col

        val result = buildString {
            do {
                take()
            }
            while (match { isLetterOrDigit() || this == '_' })
        }.lowercase()

        val region = start..col

        val context = Context(location, region, getLine(location))

        val keyword = keywords[result]

        if (keyword != null) {
            return Token(context, keyword)
        }

        val literal = literals[result]

        if (literal != null) {
            return Token(context, Token.Type.VALUE, literal)
        }

        return Token(context, Token.Type.WORD, result)
    }

    private fun unicode(size: Int): Char {
        val result = buildString {
            repeat(size) {
                if (!match { this in "0123456789ABCDEFabcdef" }) {
                    val context = Context(here(), col..(col + 1), getLine())

                    KBError.illegalUnicodeEscapeDigit(peek(), context)
                }

                take()
            }
        }

        return result.toInt(16).toChar()
    }

    private fun escape(delimiter: Char): Char {
        mustSkip('\\')

        return when {
            skip('0')              -> '\u0000'

            skip('B') || skip('b') -> '\b'

            skip('N') || skip('n') -> '\n'

            skip('T') || skip('t') -> '\t'

            skip('U') || skip('u') -> unicode(4)

            skip('X') || skip('x') -> unicode(2)

            skip('\\')             -> '\\'

            skip(delimiter)        -> delimiter

            else                   -> {
                val context = Context(here(), col..(col + 1), getLine())

                KBError.invalidEscapeCharacter(peek(), context)
            }
        }
    }

    private fun char(): Token {
        val location = here()

        val start = col

        mustSkip('\'')

        val result = if (match('\\')) {
            escape('\'')
        }
        else {
            val char = peek()

            mustSkip(char)

            char
        }

        mustSkip('\'')

        val region = start..col

        val context = Context(location, region, getLine(location))

        return Token(context, Token.Type.VALUE, result)
    }

    private fun string(): Token {
        val location = here()

        val start = col

        mustSkip('"')

        val result = buildString {
            while (!skip('"')) {
                if (match('\\')) {
                    append(escape('"'))
                }
                else {
                    take()
                }
            }
        }

        val region = start..col

        val context = Context(location, region, getLine(location))

        return Token(context, Token.Type.VALUE, result)
    }

    private fun symbol(): Token {
        val location = here()

        val start = col

        val op = when {
            skip('=') -> when {
                skip('=') -> Token.Type.DOUBLE_EQUAL

                else      -> Token.Type.EQUAL_SIGN
            }

            skip('+') -> when {
                skip('=') -> Token.Type.PLUS_EQUAL

                else      -> Token.Type.PLUS
            }

            skip('-') -> when {
                skip('=') -> Token.Type.DASH_EQUAL

                else      -> Token.Type.DASH
            }

            skip('*') -> when {
                skip('=') -> Token.Type.STAR_EQUAL

                else      -> Token.Type.STAR
            }

            skip('/') -> when {
                skip('=') -> Token.Type.SLASH_EQUAL

                else      -> Token.Type.SLASH
            }

            skip('%') -> when {
                skip('=') -> Token.Type.PERCENT_EQUAL

                else      -> Token.Type.PERCENT
            }

            skip('<') -> when {
                skip('>') -> Token.Type.LESS_GREATER

                skip('=') -> Token.Type.LESS_EQUAL_SIGN

                else      -> Token.Type.LESS_SIGN
            }

            skip('>') -> when {
                skip('=') -> Token.Type.GREATER_EQUAL_SIGN

                else      -> Token.Type.GREATER_SIGN
            }

            skip('&') -> when {
                skip('=') -> Token.Type.AMPERSAND_EQUAL

                else      -> Token.Type.AMPERSAND
            }

            skip('$') -> Token.Type.DOLLAR

            skip('#') -> Token.Type.POUND

            skip('@') -> Token.Type.AT

            skip('.') -> Token.Type.DOT

            skip(':') -> when {
                skip(':') -> Token.Type.DOUBLE_COLON

                else      -> Token.Type.COLON
            }

            skip('(') -> Token.Type.LEFT_PAREN

            skip(')') -> Token.Type.RIGHT_PAREN

            skip('[') -> Token.Type.LEFT_SQUARE

            skip(']') -> Token.Type.RIGHT_SQUARE

            skip('{') -> Token.Type.LEFT_BRACE

            skip('}') -> Token.Type.RIGHT_BRACE

            skip(',') -> Token.Type.COMMA

            else      -> {
                val context = Context(here(), col..(col + 1), getLine())

                KBError.illegalCharacter(peek(), context)
            }
        }

        val region = start..col

        val context = Context(location, region, getLine(location))

        return Token(context, op)
    }

    private fun endOfFile(): Token {
        val location = here()

        val region = pos..pos

        val context = Context(location, region, getLine(location))

        return Token(context, Token.Type.END_OF_FILE)
    }
}
