package kakkoiichris.kb.util

import kakkoiichris.kb.lexer.Lexer
import kakkoiichris.kb.parser.Parser
import kakkoiichris.kb.parser.Stmt

class Source(val name: String, val content: String) {
    companion object {
        fun readLocal(path: String): Source {
            val name = path.substring(path.lastIndexOf('/'), path.indexOf('.'))
            
            val text = Source::class
                .java
                .getResourceAsStream(path)
                ?.bufferedReader()
                ?.readText()
                ?: KBError.failure("Could not load local file '$path'!")
            
            return Source(name, text)
        }
    }
    
    fun compile(): List<Stmt> {
        val lexer = Lexer(this)
        
        val parser = Parser(lexer)
        
        return parser.parse()
    }
}