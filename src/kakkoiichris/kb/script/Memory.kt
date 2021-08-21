package kakkoiichris.kb.script

import kakkoiichris.kb.parser.Expr
import kakkoiichris.kb.parser.Stmt
import kakkoiichris.kb.util.Stack

typealias Subs = MutableList<Stmt.Sub>

fun emptySubs() = mutableListOf<Stmt.Sub>()

class Memory {
    private val stack = Stack<Scope>()
    
    private var n = 0
    
    fun push(scope: Scope = Scope("\$$n", stack.peek())) {
        stack.push(scope)
        
        n++
    }
    
    fun push(name: String) {
        stack.push(Scope(name, stack.peek()))
        
        n++
    }
    
    fun pop() {
        n--
        
        stack.pop()
    }
    
    fun new(constant: Boolean, name: Expr.Name, type: DataType, value: Any) =
        stack.peek()?.new(constant, name.value, type, value) ?: error("NEW: NO ACTIVE SCOPE")
    
    fun newLet(name: Expr.Name, type: DataType, value: Any) =
        stack.peek()?.newLet(name, type, value) ?: error("NEW LET: NO ACTIVE SCOPE")
    
    fun getRef(name: Expr.Name): Scope.Reference? =
        stack.peek()?.getRef(name)
    
    fun newSub(sub: Stmt.Sub) =
        stack.peek()?.newSub(sub) ?: error("NEW LET: NO ACTIVE SCOPE")
    
    fun getSubs(name: Expr.Name) =
        stack.peek()?.getSubs(name)
    
    fun peek() =
        stack.peek()
    
    open class Scope(private val id: String, private val parent: Scope? = null) {
        val references = mutableMapOf<String, Reference>()
        
        private val allSubs = mutableMapOf<String, Subs>()
        
        val datas = mutableMapOf<String, Stmt.Data>()
        
        fun new(constant: Boolean, name: String, type: DataType, value: Any): Boolean {
            return if (references[name] == null) {
                references[name] = Reference(constant, type, value)
                
                true
            }
            else {
                false
            }
        }
    
        fun newLet(name: Expr.Name, type: DataType, value: Any) =
            new(true, name.value, type, value)
    
        fun getRef(name: Expr.Name): Reference? =
            getRef(name.value)
        
        fun getRef(name: String): Reference? =
            references[name] ?: parent?.getRef(name)
        
        fun newSub(sub: Stmt.Sub): Boolean {
            if (allSubs[sub.name.value] == null) {
                allSubs[sub.name.value] = emptySubs()
            }
            
            val subs = allSubs[sub.name.value]!!
            
            for (other in subs) {
                if (sub.fullSignature == other.fullSignature) {
                    return false
                }
            }
            
            subs += sub
            
            return true
        }
        
        fun getSubs(name: Expr.Name) =
            getSubs(name.value)
        
        private fun getSubs(name: String): Subs? =
            allSubs[name] ?: parent?.getSubs(name)
    
        override fun toString() = "Scope $id"
        
        data class Reference(val constant: Boolean, val type: DataType, var value: Any) {
            fun put(script: Script, x: Any): Boolean? {
                if (constant) {
                    return null
                }
                
                return if (type.matches(script, x) != null) {
                    value = x
                    
                    true
                }
                else false
            }
        }
    }
}

fun Any.fromRef() =
    (this as? Memory.Scope.Reference)?.value ?: this