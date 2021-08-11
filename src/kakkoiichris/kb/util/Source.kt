package kakkoiichris.kb.util

import kakkoiichris.kb.lexer.Lexer
import kakkoiichris.kb.parser.Parser
import kakkoiichris.kb.parser.Stmt

class Source(val name: String, val text: String) {
    companion object {
        fun readLocal(path: String): Source {
            val name = path.substring(path.lastIndexOf('/'), path.indexOf('.'))
            
            val text = Source::class
                .java
                .getResourceAsStream(path)
                ?.bufferedReader()
                ?.readText()
                ?: error("LOCAL FILE UNAVAILABLE")
            
            return Source(name, text)
        }
    }
    
    fun compile(): List<Stmt> {
        val lexer = Lexer(this)
        
        val tokens = lexer.lex()
        
        val parser = Parser(tokens)
        
        return parser.parse()
    }
}