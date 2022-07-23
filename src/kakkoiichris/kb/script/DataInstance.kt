package kakkoiichris.kb.script

import kakkoiichris.kb.lexer.Location
import kakkoiichris.kb.parser.Expr
import kakkoiichris.kb.parser.toExpr
import kakkoiichris.kb.parser.toName

class DataInstance(val name: Expr.Name, private val members: Memory.Scope) {
    fun deref() =
        members.references.values.map { it.fromRef() }
    
    operator fun get(name: Expr.Name) =
        members.getRef(name)
    
    operator fun get(name: String) =
        members.getRef(name)
    
    fun invokeSub(script: Script, subName: Expr.Name, vararg otherArgs: Any): Any {
        val invoke = Expr.Invoke(Location.none, subName, listOf(this, *otherArgs).map { it.toExpr() })
        
        return script.visitInvokeExpr(invoke)
    }
    
    fun invokeUnaryOperator(script: Script, operator: Expr.Unary.Operator): Any {
        val invoke = Expr.Invoke(Location.none, operator.name.lowercase().toName(), listOf(this.toExpr()))
        
        return script.visitInvokeExpr(invoke)
    }
    
    fun invokeBinaryOperator(script: Script, operator: Expr.Binary.Operator, otherArg: Any): Any {
        val invoke = Expr.Invoke(Location.none, operator.name.lowercase().toName(), listOf(this, otherArg).map { it.toExpr() })
        
        return script.visitInvokeExpr(invoke)
    }
    
    fun isEmpty(): Boolean {
        for ((_, member) in members.references) {
            if (!member.fromRef().isEmptyValue()) {
                return false
            }
        }
        
        return true
    }
    
    override fun toString() =
        members
            .references
            .entries
            .joinToString(prefix = "$name { ", postfix = " }") { (name, ref) ->
                "$name : ${ref.fromRef()}"
            }
}