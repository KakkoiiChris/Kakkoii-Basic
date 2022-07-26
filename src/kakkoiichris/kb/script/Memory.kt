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
    
    fun hasRef(name: Expr.Name) =
        stack.peek()?.hasRef(name) ?: error("NEW LET: NO ACTIVE SCOPE")
    
    fun newRef(constant: Boolean, name: Expr.Name, type: DataType, value: Any) =
        stack.peek()?.newRef(constant, name.value, type, value) ?: error("NEW: NO ACTIVE SCOPE")
    
    fun getRef(name: Expr.Name): Scope.Reference? =
        stack.peek()?.getRef(name)
    
    fun newSub(sub: Stmt.Sub) =
        stack.peek()?.newSub(sub) ?: error("NEW LET: NO ACTIVE SCOPE")
    
    fun getSubs(name: Expr.Name) =
        stack.peek()?.getSubs(name)
    
    fun newData(data: Stmt.Data) =
        stack.peek()?.newData(data) ?: error("NEW LET: NO ACTIVE SCOPE")
    
    fun getData(name: Expr.Name) =
        stack.peek()?.getData(name)
    
    fun peek() =
        stack.peek()
    
    open class Scope(private val id: String, private val parent: Scope? = null) {
        val references = mutableMapOf<String, Reference>()
        
        private val allSubs = mutableMapOf<String, Subs>()
        
        private val datas = mutableMapOf<String, Stmt.Data>()
        
        fun hasRef(name: Expr.Name) =
            references.containsKey(name.value)
        
        fun newRef(constant: Boolean, name: String, type: DataType, value: Any) {
            references[name] = Reference(constant, type, value)
        }
        
        fun getRef(name: Expr.Name): Reference? =
            getRef(name.value)
        
        fun getRef(name: String): Reference? =
            references[name] ?: parent?.getRef(name)
        
        fun newSub(sub: Stmt.Sub): Boolean {
            if (allSubs[sub.name.value.lowercase()] == null) {
                allSubs[sub.name.value.lowercase()] = emptySubs()
            }
            
            val subs = allSubs[sub.name.value.lowercase()]!!
            
            for (other in subs) {
                if (sub.signature == other.signature) {
                    return false
                }
            }
            
            subs += sub
            
            return true
        }
        
        fun getSubs(name: Expr.Name) =
            getSubs(name.value)
        
        private fun getSubs(name: String): Subs? =
            allSubs[name.lowercase()] ?: parent?.getSubs(name)
        
        fun newData(data: Stmt.Data) =
            if (datas[data.name.value] == null) {
                datas[data.name.value] = data
                
                true
            }
            else {
                false
            }
        
        fun getData(name: Expr.Name) =
            getData(name.value)
        
        private fun getData(name: String): Stmt.Data? {
            val here = datas[name]
            
            return here ?: parent?.getData(name)
        }
        
        override fun toString() = "Scope $id"
        
        data class Reference(val constant: Boolean, val type: DataType, var value: Any) {
            fun put(script: Script, x: Any): Boolean? {
                if (constant) {
                    return null
                }
                
                return if (type.filter(script, x) != null) {
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