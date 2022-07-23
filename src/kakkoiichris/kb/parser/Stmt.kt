package kakkoiichris.kb.parser

import kakkoiichris.kb.lexer.Location
import kakkoiichris.kb.script.DataType
import kakkoiichris.kb.script.Memory

sealed class Stmt(val location: Location, val label: String) {
    open val detail get() = ""
    
    abstract fun <X> accept(visitor: Visitor<X>): X
    
    val trace get() = "${javaClass.simpleName}$detail$location"
    
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
        
        fun visitGotoStmt(stmt: Goto): X
        
        fun visitExpressionStmt(stmt: Expression): X
    }
    
    override fun toString() = trace
    
    object None : Stmt(Location.none, "") {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitNoneStmt(this)
    }
    
    class Decl(
        location: Location,
        label: String,
        val constant: Boolean,
        val name: Expr.Name,
        val type: Expr.Type,
        val expr: Expr,
    ) :
        Stmt(location, label) {
        val isVararg get() = type.value is DataType.Vararg
        
        override val detail get() = " $name"
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitDeclStmt(this)
        
        fun withExpr(expr: Expr) =
            Decl(location, label, constant, name, type, expr)
        
        fun withValue(x: Any) =
            Decl(location, label, constant, name, type, x.toExpr())
    }
    
    class DeclEach(
        location: Location,
        label: String,
        val constant: Boolean,
        val pairs: List<Pair<Expr.Name, Expr.Type>>,
        val expr: Expr,
    ) : Stmt(location, label) {
        override val detail get() = " ${pairs.map { (name, _) -> name }.joinToString(separator = ", ")}"
        
        fun withValue(x: Any) =
            DeclEach(location, label, constant, pairs, x.toExpr())
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitDeclEachStmt(this)
    }
    
    class Block(location: Location, val stmts: List<Stmt>) : Stmt(location, "") {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitBlockStmt(this)
    }
    
    class Do(location: Location, label: String, val body: Block) : Stmt(location, label) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitDoStmt(this)
    }
    
    class If(location: Location, label: String, val branches: List<Pair<Expr, Block>>, val elze: Stmt) : Stmt(location, label) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitIfStmt(this)
    }
    
    class Switch(location: Location, label: String, val subject: Expr, val cases: List<Case>) : Stmt(location, label) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitSwitchStmt(this)
        
        sealed class Case(val location: Location, val block: Block) {
            class Values(location: Location, val tests: List<Expr.Value>, block: Block) : Case(location, block)
            
            class Type(location: Location, val type: Expr.Type, block: Block) : Case(location, block)
            
            class Else(location: Location, block: Block) : Case(location, block)
        }
    }
    
    class While(location: Location, label: String, val test: Expr, val body: Block) : Stmt(location, label) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitWhileStmt(this)
    }
    
    class Until(location: Location, label: String, val test: Expr, val body: Block) : Stmt(location, label) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitUntilStmt(this)
    }
    
    class ForCounter(location: Location, label: String, val decl: Decl, val to: Expr, val step: Expr, val body: Block) :
        Stmt(location, label) {
        override val detail get() = decl.detail
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitForCounterStmt(this)
    }
    
    class ForIterate(location: Location, label: String, val decl: Decl, val iterable: Expr, val body: Block) :
        Stmt(location, label) {
        override val detail get() = decl.detail
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitForIterateStmt(this)
    }
    
    class ForIterateEach(location: Location, label: String, val decl: DeclEach, val iterable: Expr, val body: Block) :
        Stmt(location, label) {
        override val detail get() = decl.detail
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitForIterateEachStmt(this)
    }
    
    class Data(location: Location, label: String, val name: Expr.Name, val decls: List<Decl>) : Stmt(location, label) {
        override val detail get() = " $name"
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitDataStmt(this)
    }
    
    class Sub(
        location: Location,
        label: String,
        val name: Expr.Name,
        val params: List<Decl>,
        val type: Expr.Type,
        val body: Block,
    ) : Stmt(location, label) {
        val signature
            get() = params.joinToString(prefix = "$name(", separator = ",", postfix = ")") {
                it.type.value.toString()
            }
        
        var scope = Memory.Scope("")
    
        override val detail get() = " $signature"
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitSubStmt(this)
    }
    
    class Break(location: Location, label: String, val destination: String) : Stmt(location, label) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitBreakStmt(this)
    }
    
    class Next(location: Location, label: String, val pointer: Expr.Name) : Stmt(location, label) {
        override val detail get() = " $pointer"
    
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitNextStmt(this)
    }
    
    class Return(location: Location, label: String) : Stmt(location, label) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitReturnStmt(this)
    }
    
    class Yield(location: Location, label: String, val value: Expr) : Stmt(location, label) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitYieldStmt(this)
    }
    
    class Goto(location: Location, label: String, val destination: String) : Stmt(location, label) {
        override val detail get() = " $destination"
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitGotoStmt(this)
    }
    
    class Expression(label: String, val expr: Expr) : Stmt(expr.location, label) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitExpressionStmt(this)
    }
}