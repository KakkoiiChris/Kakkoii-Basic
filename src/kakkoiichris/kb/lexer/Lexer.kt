package kakkoiichris.kb.lexer

import kakkoiichris.kb.script.Empty
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
                
                match('@')           -> label()
                
                else                 -> symbol()
            }
        }
        
        return Token(here(), Token.Type.EndOfFile)
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
            KBError.invalidCharacter(peek(), char, here())
        }
    }
    
    @Suppress("SameParameterValue")
    private fun mustSkip(string: String) {
        if (!skip(string)) {
            KBError.invalidSequence(look(string.length), string, here())
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
        
        val number: Number = when {
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
        
        return Token(location, Token.Type.Value, number)
    }
    
    private fun word(): Token {
        val location = here()
        
        val result = buildString {
            do {
                take()
            }
            while (match { isLetterOrDigit() || this == '_' })
        }.lowercase()
        
        val keyword = keywords[result]
        
        if (keyword != null) {
            return Token(location, keyword)
        }
        
        val literal = literals[result]
        
        if (literal != null) {
            return Token(location, Token.Type.Value, literal)
        }
        
        return Token(location, Token.Type.Word, result)
    }
    
    private fun unicode(size: Int): Char {
        val result = buildString {
            repeat(size) {
                if (!match { this in "0123456789ABCDEFabcdef" }) {
                    KBError.illegalUnicodeEscapeDigit(peek(), here())
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
            
            else                   -> KBError.invalidEscapeCharacter(peek(), here())
        }
    }
    
    private fun char(): Token {
        val location = here()
        
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
        
        return Token(location, Token.Type.Value, result)
    }
    
    private fun string(): Token {
        val location = here()
        
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
        
        return Token(location, Token.Type.Value, result)
    }
    
    private fun label(): Token {
        val location = here()
        
        mustSkip('@')
        
        val result = buildString {
            while (match(Char::isLetterOrDigit)) {
                take()
            }
        }
        
        return Token(location, Token.Type.Label, result)
    }
    
    private fun symbol(): Token {
        val location = here()
        
        val op = when {
            skip('=') -> when {
                skip('=') -> Token.Type.DoubleEqual
                
                else      -> Token.Type.EqualSign
            }
            
            skip('+') -> when {
                skip('=') -> Token.Type.PlusEqual
                
                else      -> Token.Type.Plus
            }
            
            skip('-') -> when {
                skip('=') -> Token.Type.MinusEqual
                
                else      -> Token.Type.Minus
            }
            
            skip('*') -> when {
                skip('=') -> Token.Type.StarEqual
                
                else      -> Token.Type.Star
            }
            
            skip('/') -> when {
                skip('=') -> Token.Type.SlashEqual
                
                else      -> Token.Type.Slash
            }
            
            skip('%') -> when {
                skip('=') -> Token.Type.PercentEqual
                
                else      -> Token.Type.Percent
            }
            
            skip('<') -> when {
                skip('>') -> Token.Type.LessGreater
                
                skip('=') -> Token.Type.LessEqualSign
                
                else      -> Token.Type.LessSign
            }
            
            skip('>') -> when {
                skip('=') -> Token.Type.GreaterEqualSign
                
                else      -> Token.Type.GreaterSign
            }
            
            skip('$') -> Token.Type.Dollar
            
            skip('&') -> Token.Type.Ampersand
            
            skip('#') -> Token.Type.Pound
            
            skip('.') -> Token.Type.Dot
            
            skip(':') -> Token.Type.Colon
            
            skip('(') -> Token.Type.LeftParen
            
            skip(')') -> Token.Type.RightParen
            
            skip('[') -> Token.Type.LeftSquare
            
            skip(']') -> Token.Type.RightSquare
            
            skip('{') -> Token.Type.LeftBrace
            
            skip('}') -> Token.Type.RightBrace
            
            skip(',') -> Token.Type.Comma
            
            else      -> KBError.illegalCharacter(peek(), here())
        }
        
        return Token(location, op)
    }
}