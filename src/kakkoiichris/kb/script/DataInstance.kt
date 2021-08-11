package kakkoiichris.kb.script

import kakkoiichris.kb.parser.Expr

class DataInstance(val name: Expr.Name, private val members: Memory.Scope) {
    fun deref() =
        members.references.values.map { it.fromRef() }
    
    operator fun get(name: Expr.Name) =
        members.getRef(name) ?: TODO("NO MEMBER")
    
    operator fun get(name: String) =
        members.getRef(name) ?: TODO()
    
    override fun toString() =
        members
            .references
            .entries
            .joinToString(prefix = "$name { ", postfix = " }") { (name, ref) ->
                "$name : ${ref.fromRef().toString()}"
            }
}