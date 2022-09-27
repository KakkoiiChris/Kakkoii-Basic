package kakkoiichris.kb.parser

import kakkoiichris.kb.lexer.Location
import kakkoiichris.kb.script.DataType
import kakkoiichris.kb.script.Memory

sealed class Stmt(val location: Location) {
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
        
        fun visitTypeStmt(stmt: Type): X
        
        fun visitEnumStmt(stmt: Enum): X
        
        fun visitExpressionStmt(stmt: Expression): X
    }
    
    override fun toString() = trace
    
    object None : Stmt(Location.none) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitNoneStmt(this)
    }
    
    class Decl(
        location: Location,
        val constant: Boolean,
        val name: Expr.Name,
        val type: Expr.Type,
        val expr: Expr,
    ) :
        Stmt(location) {
        val isVararg get() = type.value is DataType.Vararg
        
        override val detail get() = " $name"
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitDeclStmt(this)
        
        fun withExpr(expr: Expr) =
            Decl(location, constant, name, type, expr)
        
        fun withValue(x: Any) =
            Decl(location, constant, name, type, x.toExpr())
    }
    
    class DeclEach(
        location: Location,
        val constant: Boolean,
        val pairs: List<Pair<Expr.Name, Expr.Type>>,
        val expr: Expr,
    ) : Stmt(location) {
        override val detail get() = " ${pairs.map { (name, _) -> name }.joinToString(separator = ", ")}"
        
        fun withValue(x: Any) =
            DeclEach(location, constant, pairs, x.toExpr())
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitDeclEachStmt(this)
    }
    
    class Block(location: Location, val stmts: List<Stmt>) : Stmt(location) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitBlockStmt(this)
    }
    
    class Do(location: Location, val label: Expr.Name, val body: Block) : Stmt(location) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitDoStmt(this)
    }
    
    class If(location: Location, val test: Expr, val body: Block, val elze: Stmt) : Stmt(location) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitIfStmt(this)
    }
    
    class Switch(location: Location, val subject: Expr, val cases: List<Case>) : Stmt(location) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitSwitchStmt(this)
        
        sealed class Case(val location: Location, val block: Block) {
            class Values(location: Location, val tests: List<Expr.Value>, block: Block) : Case(location, block)
            
            class Type(location: Location, val inverted: Boolean, val type: Expr.Type, block: Block) : Case(location, block)
            
            class Else(location: Location, block: Block) : Case(location, block)
        }
    }
    
    class While(location: Location, val label: Expr.Name, val test: Expr, val body: Block) : Stmt(location) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitWhileStmt(this)
    }
    
    class Until(location: Location, val label: Expr.Name, val test: Expr, val body: Block) : Stmt(location) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitUntilStmt(this)
    }
    
    class ForCounter(location: Location, val label: Expr.Name, val decl: Decl, val to: Expr, val step: Expr, val body: Block) :
        Stmt(location) {
        override val detail get() = decl.detail
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitForCounterStmt(this)
    }
    
    class ForIterate(location: Location, val label: Expr.Name, val decl: Decl, val iterable: Expr, val body: Block) :
        Stmt(location) {
        override val detail get() = decl.detail
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitForIterateStmt(this)
    }
    
    class ForIterateEach(location: Location, val label: Expr.Name, val decl: DeclEach, val iterable: Expr, val body: Block) :
        Stmt(location) {
        override val detail get() = decl.detail
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitForIterateEachStmt(this)
    }
    
    class Data(location: Location, val name: Expr.Name, val decls: List<Decl>) : Stmt(location) {
        override val detail get() = " $name"
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitDataStmt(this)
    }
    
    class Sub(
        location: Location,
        val name: Expr.Name,
        val type: Expr.Type,
        val params: List<Decl>,
        val body: Block,
    ) : Stmt(location) {
        val signature
            get() = params.joinToString(prefix = "$name(", separator = ",", postfix = ")") {
                it.type.value.toString()
            }
        
        val isLinked = body.stmts.isEmpty()
        
        var scope = Memory.Scope("")
        
        override val detail get() = " $signature"
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitSubStmt(this)
    }
    
    class Break(location: Location, val destination: Expr.Name) : Stmt(location) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitBreakStmt(this)
    }
    
    class Next(location: Location, val destination: Expr.Name) : Stmt(location) {
        override val detail get() = " $destination"
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitNextStmt(this)
    }
    
    class Return(location: Location) : Stmt(location) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitReturnStmt(this)
    }
    
    class Yield(location: Location, val value: Expr) : Stmt(location) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitYieldStmt(this)
    }
    
    class Type(location: Location, val type: Expr.Type, val alias: Expr.Name) : Stmt(location) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitTypeStmt(this)
    }
    
    class Enum(location: Location, val name: Expr.Name, val subType: Expr.Type, val start: Expr, val step: Expr, val entries: List<Entry>) : Stmt(location) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitEnumStmt(this)
        
        class Entry(val location: Location, val name: Expr.Name, val value: Expr)
    }
    
    class Expression(val expr: Expr) : Stmt(expr.location) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitExpressionStmt(this)
    }
}