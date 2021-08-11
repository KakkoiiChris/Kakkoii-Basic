package kakkoiichris.kb.parser

import kakkoiichris.kb.lexer.Location
import kakkoiichris.kb.script.DataType

sealed class Stmt(val loc: Location) {
    abstract fun <X> accept(visitor: Visitor<X>): X
    
    interface Visitor<X> {
        fun visit(stmt: Stmt) =
            stmt.accept(this)
        
        fun visitNoneStmt(stmt: None): X
        
        fun visitDeclStmt(stmt: Decl): X
        
        fun visitBlockStmt(stmt: Block): X
        
        fun visitDoStmt(stmt: Do): X
        
        fun visitIfStmt(stmt: If): X
        
        fun visitWhileStmt(stmt: While): X
        
        fun visitForStmt(stmt: For): X
        
        fun visitForeachStmt(stmt: Foreach): X
        
        fun visitDataStmt(stmt: Data): X
        
        fun visitSubStmt(stmt: Sub): X
        
        fun visitBreakStmt(stmt: Break): X
        
        fun visitNextStmt(stmt: Next): X
        
        fun visitReturnStmt(stmt: Return): X
    
        fun visitYieldStmt(stmt: Yield): X
        
        fun visitExpressionStmt(stmt: Expression): X
    }
    
    object None : Stmt(Location.none) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitNoneStmt(this)
    }
    
    class Decl(loc: Location, val constant: Boolean, val name: Expr.Name, val type: Expr.Type, val expr: Expr) : Stmt(loc) {
        val isVararg get() = type.value is DataType.Vararg
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitDeclStmt(this)
        
        fun withNewType(type:DataType)=
            Decl(loc, constant, name, type.toType(), expr)
    
        fun with(expr: Expr) =
            Decl(loc, constant, name, type, expr)
    }
    
    class Block(loc: Location, val stmts: List<Stmt>) : Stmt(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitBlockStmt(this)
    }
    
    class Do(loc: Location, val body: Block) : Stmt(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitDoStmt(this)
    }
    
    class If(loc: Location, val branches: List<Pair<Expr, Block>>, val elze: Stmt) : Stmt(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitIfStmt(this)
    }
    
    class While(loc: Location, val test: Expr, val body: Block) : Stmt(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitWhileStmt(this)
    }
    
    class For(loc: Location, val decl: Decl, val to: Expr, val step: Expr, val body: Block) : Stmt(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitForStmt(this)
    }
    
    class Foreach(loc: Location, val decl: Decl, val iterable: Expr, val body: Block) : Stmt(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitForeachStmt(this)
    }
    
    class Data(loc: Location, val name: Expr.Name, val decls: List<Decl>) : Stmt(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitDataStmt(this)
    }
    
    class Sub(loc: Location, val name: Expr.Name, val params: List<Decl>, val type: Expr.Type, val body: Block) : Stmt(loc) {
        val fullSignature get() = params.joinToString(prefix = "$name(", separator = ",", postfix = ")") { it.type.value.toString() }
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitSubStmt(this)
    }
    
    class Break(loc: Location) : Stmt(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitBreakStmt(this)
    }
    
    class Next(loc: Location, val pointer:Expr.Name) : Stmt(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitNextStmt(this)
    }
    
    class Return(loc: Location) : Stmt(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitReturnStmt(this)
    }
    
    class Yield(loc: Location, val value: Expr) : Stmt(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitYieldStmt(this)
    }
    
    class Expression(val expr: Expr) : Stmt(expr.loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitExpressionStmt(this)
    }
}