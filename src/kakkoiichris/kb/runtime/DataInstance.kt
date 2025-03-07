package kakkoiichris.kb.runtime

import kakkoiichris.kb.lexer.Context
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

    fun invokeSub(runtime: Runtime, subName: Expr.Name, vararg otherArgs: Any): Any {
        val invoke = Expr.Invoke(
            Context.none,
            subName,
            listOf(this, *otherArgs).map { Expr.Invoke.Argument(false, it.toExpr()) })

        return runtime.visitInvokeExpr(invoke)
    }

    fun invokeUnaryOperator(runtime: Runtime, operator: Expr.Unary.Operator): Any {
        val invoke =
            Expr.Invoke(Context.none, operator.name.lowercase().toName(), listOf(Expr.Invoke.Argument(false, toExpr())))

        return runtime.visitInvokeExpr(invoke)
    }

    fun invokeBinaryOperator(runtime: Runtime, operator: Expr.Binary.Operator, otherArg: Any): Any {
        val invoke = Expr.Invoke(
            Context.none,
            operator.name.lowercase().toName(),
            listOf(this, otherArg).map { Expr.Invoke.Argument(false, it.toExpr()) })

        return runtime.visitInvokeExpr(invoke)
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