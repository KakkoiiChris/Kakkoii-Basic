package kakkoiichris.kb.lexer

import kakkoiichris.kb.util.KBError
import kakkoiichris.kb.util.Source

class Lexer(private val source: Source) {
    companion object {
        const val NUL = '\u0000'
        
        val keywords = mapOf(
            Token.Type.Let.kw,
            Token.Type.Var.kw,
            Token.Type.If.kw,
            Token.Type.Elif.kw,
            Token.Type.Else.kw,
            Token.Type.While.kw,
            Token.Type.Do.kw,
            Token.Type.For.kw,
            Token.Type.To.kw,
            Token.Type.Step.kw,
            Token.Type.In.kw,
            Token.Type.Data.kw,
            Token.Type.Sub.kw,
            Token.Type.Break.kw,
            Token.Type.Next.kw,
            Token.Type.Return.kw,
            Token.Type.Yield.kw,
            Token.Type.End.kw,
            Token.Type.Void.kw,
            Token.Type.Bool.kw,
            Token.Type.Byte.kw,
            Token.Type.Short.kw,
            Token.Type.Int.kw,
            Token.Type.Long.kw,
            Token.Type.Float.kw,
            Token.Type.Double.kw,
            Token.Type.Char.kw,
            Token.Type.String.kw,
            Token.Type.Any.kw,
            Token.Type.Or.kw,
            Token.Type.And.kw,
            Token.Type.Is.kw,
            Token.Type.As.kw,
            Token.Type.Not.kw
        )
        
        val literals = mapOf(
            "true" to true,
            "false" to false
        )
        
        val longRegex = """\d+[Ll]""".toRegex()
        val intRegex = """\d+""".toRegex()
        val floatRegex = """\d+(\.\d+)?([Ee][+-]?\d+)?[Ff]""".toRegex()
        val doubleRegex = """\d+(\.\d+)?([Ee][+-]?\d+)?[Dd]?""".toRegex()
        val longBinaryRegex = """0b[01]+[Ll]""".toRegex()
        val longHexRegex = """0x[0-9A-Fa-f]+[Ll]""".toRegex()
        val intBinaryRegex = """0b[01]+""".toRegex()
        val intHexRegex = """0x[0-9A-Fa-f]+""".toRegex()
    }
    
    private var pos = 0
    private var row = 1
    private var col = 1
    
    fun lex(): List<Token> {
        val tokens = mutableListOf<Token>()
        
        while (!eof()) {
            if (match { isWhitespace() }) {
                skipWhitespace()
                continue
            }
            
            if (match("rem")) {
                skipLineComment()
                continue
            }
            
            tokens += when {
                match { isDigit() }  -> number()
                
                match { isLetter() } -> word()
                
                match('\'')          -> char()
                
                match('"')           -> string()
                
                else                 -> operator()
            }
        }
        
        tokens += Token(here(), Token.Type.EndOfFile)
        
        return tokens
    }
    
    private fun peek(offset: Int = 0) =
        if (pos + offset in source.text.indices)
            source.text[pos + offset]
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
            KBError.forLexer("Character '${peek()}' is invalid; expected $char!", here())
        }
    }
    
    @Suppress("SameParameterValue")
    private fun mustSkip(string: String) {
        if (!skip(string)) {
            KBError.forLexer("Sequence '${look(string.length)}' is invalid; expected $string!", here())
        }
    }
    
    private fun eof() =
        match(NUL)
    
    @Suppress("ControlFlowWithEmptyBody")
    private fun skipWhitespace() {
        while (skip { isWhitespace() });
    }
    
    private fun skipLineComment() {
        mustSkip("rem")
        
        while (!skip('\n')) {
            step()
        }
    }
    
    private fun StringBuilder.take() {
        append(peek())
        step()
    }
    
    @Suppress("IMPLICIT_CAST_TO_ANY")
    private fun number(): Token {
        val loc = here()
        
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
        
        val number = when {
            result.matches(longRegex)       -> result.dropLast(1).toLong()
            
            result.matches(intRegex)        -> result.toInt()
            
            result.matches(floatRegex)      -> result.dropLast(1).toFloat()
            
            result.matches(doubleRegex)     -> result.toDouble()
            
            result.matches(longBinaryRegex) -> result.substring(2, result.length - 1).toInt(2)
            
            result.matches(longHexRegex)    -> result.substring(2, result.length - 1).toInt(16)
            
            result.matches(intBinaryRegex)  -> result.substring(2).toInt(2)
            
            result.matches(intHexRegex)     -> result.substring(2).toInt(16)
            
            else                            -> KBError.forLexer("Unexpected number '$result'!", loc)
        }
        
        return Token(loc, Token.Type.Value, number)
    }
    
    private fun word(): Token {
        val loc = here()
        
        val result = buildString {
            do {
                take()
            }
            while (match { isLetterOrDigit() || this == '_' })
        }
        
        val keyword = keywords[result.lowercase()]
        
        if (keyword != null) {
            return Token(loc, keyword)
        }
        
        val literal = literals[result.lowercase()]
        
        if (literal != null) {
            return Token(loc, Token.Type.Value, literal)
        }
        
        return Token(loc, Token.Type.Word, result)
    }
    
    private fun unicode(size: Int): Char {
        val loc = here()
        
        val result = buildString {
            repeat(size) {
                take()
            }
        }
        
        return result.toIntOrNull(16)?.toChar() ?: KBError.forLexer("Unicode value '$result' is invalid!", loc)
    }
    
    private fun escape(delimiter: Char): Char {
        mustSkip('\\')
        
        return when {
            skip('0')       -> '\u0000'
            
            skip('b')       -> '\b'
            
            skip('n')       -> '\n'
            
            skip('t')       -> '\t'
            
            skip('u')       -> unicode(4)
            
            skip('x')       -> unicode(2)
            
            skip('\\')      -> '\\'
            
            skip(delimiter) -> delimiter
            
            else            -> KBError.forLexer("Character escape '\\${peek()}' is invalid!", here())
        }
    }
    
    private fun char(): Token {
        val loc = here()
        
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
        
        return Token(loc, Token.Type.Value, result)
    }
    
    private fun string(): Token {
        val loc = here()
        
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
        
        return Token(loc, Token.Type.Value, result)
    }
    
    private fun operator(): Token {
        val loc = here()
        
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
            
            else      -> KBError.forLexer("Character '${peek()}' is invalid!", here())
        }
        
        return Token(loc, op)
    }
}