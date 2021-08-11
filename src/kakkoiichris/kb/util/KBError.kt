package kakkoiichris.kb.util

import kakkoiichris.kb.lexer.Location

class KBError(stage: String, msg: String, loc: Location) : Exception("Error @ $stage: $msg ($loc)") {
    companion object {
        fun forLexer(msg: String, loc: Location): Nothing =
            throw KBError("Lexer", msg, loc)
        
        fun forParser(msg: String, loc: Location): Nothing =
            throw KBError("Parser", msg, loc)
        
        fun forScript(msg: String, loc: Location): Nothing =
            throw KBError("Script", msg, loc)
    }
}