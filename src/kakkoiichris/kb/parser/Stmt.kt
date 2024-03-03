package kakkoiichris.kb.parser

import kakkoiichris.kb.lexer.Context
import kakkoiichris.kb.script.DataType
import kakkoiichris.kb.script.Memory

sealed class Stmt(val context: Context) {
    open val detail get() = ""

    abstract fun <X> accept(visitor: Visitor<X>): X

    val trace get() = "${javaClass.simpleName}$detail${context.location}"

    interface Visitor<X> {
        fun visit(stmt: Stmt) =
            stmt.accept(this)

        fun visitNoneStmt(stmt: None): X

        fun visitDeclStmt(stmt: Decl): X

        fun visitDeclEachStmt(stmt: DeclEach): X

        fun visitBlockStmt(stmt: Block): X

        fun visitDoStmt(stmt: Do): X

        fun visitIfStmt(stmt: If): X

        fun visitSwitchStmt(stmt: Switch): X

        fun visitWhileStmt(stmt: While): X

        fun visitUntilStmt(stmt: Until): X

        fun visitForCounterStmt(stmt: ForCounter): X

        fun visitForIterateStmt(stmt: ForIterate): X

        fun visitForIterateEachStmt(stmt: ForIterateEach): X

        fun visitDataStmt(stmt: Data): X

        fun visitSubStmt(stmt: Sub): X

        fun visitBreakStmt(stmt: Break): X

        fun visitNextStmt(stmt: Next): X

        fun visitReturnStmt(stmt: Return): X

        fun visitYieldStmt(stmt: Yield): X

        fun visitTypeStmt(stmt: Type): X

        fun visitBasicEnumStmt(stmt: BasicEnum): X

        fun visitDataEnumStmt(stmt: DataEnum): X

        fun visitExpressionStmt(stmt: Expression): X
    }

    override fun toString() = trace

    object None : Stmt(Context.none) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitNoneStmt(this)
    }

    class Decl(
        context: Context,
        val constant: Boolean,
        val definition: Definition,
        val expr: Expr,
    ) :
        Stmt(context) {
        val isVararg get() = definition.type.value is DataType.Vararg

        override val detail get() = " ${definition.name}"

        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitDeclStmt(this)

        fun withExpr(expr: Expr) =
            Decl(context, constant, definition, expr)

        fun withValue(x: Any) =
            Decl(context, constant, definition, x.toExpr())
    }

    class DeclEach(
        context: Context,
        val constant: Boolean,
        val definitions: List<Definition>,
        val expr: Expr,
    ) : Stmt(context) {
        override val detail get() = " ${definitions.map { (name, _) -> name }.joinToString(separator = ", ")}"

        fun withValue(x: Any) =
            DeclEach(context, constant, definitions, x.toExpr())

        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitDeclEachStmt(this)
    }

    class Block(context: Context, val stmts: List<Stmt>) : Stmt(context) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitBlockStmt(this)
    }

    class Do(context: Context, val label: Expr.Name, val body: Block) : Stmt(context) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitDoStmt(this)
    }

    class If(context: Context, val test: Expr, val body: Block, val `else`: Stmt) : Stmt(context) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitIfStmt(this)
    }

    class Switch(context: Context, val subject: Expr, val cases: List<Case>) : Stmt(context) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitSwitchStmt(this)

        sealed class Case(val context: Context, val block: Block) {
            class Values(context: Context, val tests: List<Expr.Value>, block: Block) : Case(context, block)

            class Type(context: Context, val inverted: Boolean, val type: Expr.Type, block: Block) :
                Case(context, block)

            class Else(context: Context, block: Block) : Case(context, block)
        }
    }

    class While(context: Context, val label: Expr.Name, val test: Expr, val body: Block) : Stmt(context) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitWhileStmt(this)
    }

    class Until(context: Context, val label: Expr.Name, val test: Expr, val body: Block) : Stmt(context) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitUntilStmt(this)
    }

    class ForCounter(
        context: Context,
        val label: Expr.Name,
        val decl: Decl,
        val to: Expr,
        val step: Expr,
        val body: Block
    ) :
        Stmt(context) {
        override val detail get() = decl.detail

        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitForCounterStmt(this)
    }

    class ForIterate(context: Context, val label: Expr.Name, val decl: Decl, val iterable: Expr, val body: Block) :
        Stmt(context) {
        override val detail get() = decl.detail

        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitForIterateStmt(this)
    }

    class ForIterateEach(
        context: Context,
        val label: Expr.Name,
        val decl: DeclEach,
        val iterable: Expr,
        val body: Block
    ) :
        Stmt(context) {
        override val detail get() = decl.detail

        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitForIterateEachStmt(this)
    }

    class Data(context: Context, val name: Expr.Name, val decls: List<Decl>) : Stmt(context) {
        override val detail get() = " $name"

        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitDataStmt(this)
    }

    class Sub(
        context: Context,
        val definition: Definition,
        val params: List<Decl>,
        val body: Block,
    ) : Stmt(context) {
        val signature
            get() = params.joinToString(prefix = "${definition.name}(", separator = ",", postfix = ")") {
                it.definition.type.value.toString()
            }

        val isLinked = body.stmts.isEmpty()

        var scope = Memory.Scope("")

        override val detail get() = " $signature"

        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitSubStmt(this)
    }

    class Break(context: Context, val destination: Expr.Name) : Stmt(context) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitBreakStmt(this)
    }

    class Next(context: Context, val destination: Expr.Name) : Stmt(context) {
        override val detail get() = " $destination"

        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitNextStmt(this)
    }

    class Return(context: Context) : Stmt(context) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitReturnStmt(this)
    }

    class Yield(context: Context, val value: Expr) : Stmt(context) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitYieldStmt(this)
    }

    class Type(context: Context, val type: Expr.Type, val alias: Expr.Name) : Stmt(context) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitTypeStmt(this)
    }

    class BasicEnum(context: Context, val name: Expr.Name, val entries: List<Entry>) : Stmt(context) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitBasicEnumStmt(this)

        data class Entry(val context: Context, val name: Expr.Name, val ordinal: Expr.Value, val value: Expr.Value)
    }

    class DataEnum(context: Context, val name: Expr.Name, val type: Expr.Type, val entries: List<Entry>) :
        Stmt(context) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitDataEnumStmt(this)

        data class Entry(
            val context: Context,
            val name: Expr.Name,
            val ordinal: Expr.Value,
            val value: Expr.Instantiate
        )
    }

    class Expression(val expr: Expr) : Stmt(expr.context) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitExpressionStmt(this)
    }
}