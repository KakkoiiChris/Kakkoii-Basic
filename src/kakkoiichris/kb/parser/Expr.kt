package kakkoiichris.kb.parser

import kakkoiichris.kb.lexer.Location
import kakkoiichris.kb.lexer.Token
import kakkoiichris.kb.script.DataType

sealed class Expr(val loc: Location) {
    abstract fun <X> accept(visitor: Visitor<X>): X
    
    interface Visitor<X> {
        fun visit(expr: Expr) =
            expr.accept(this)
        
        fun visitNoneExpr(expr: None): X
        
        fun visitValueExpr(expr: Value): X
        
        fun visitNameExpr(expr: Name): X
        
        fun visitTypeExpr(expr: Type): X
        
        fun visitArrayExpr(expr: Array): X
        
        fun visitUnaryExpr(expr: Unary): X
        
        fun visitBinaryExpr(expr: Binary): X
        
        fun visitGetExpr(expr: Get): X
        
        fun visitSetExpr(expr: Set): X
        
        fun visitInvokeExpr(expr: Invoke): X
        
        fun visitInstantiateExpr(expr: Instantiate): X
    }
    
    object None : Expr(Location.none) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitNoneExpr(this)
    }
    
    class Value(loc: Location, val value: Any) : Expr(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitValueExpr(this)
    }
    
    class Name(loc: Location, val value: String) : Expr(loc) {
        companion object {
            val none = Name(Location.none, "")
        }
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitNameExpr(this)
        
        override fun hashCode(): Int {
            return value.hashCode()
        }
        
        override fun equals(other: Any?) =
            when (other) {
                is String -> value == other
                
                is Name   -> value == other.value
                
                else      -> false
            }
        
        override fun toString() =
            value
    }
    
    class Type(loc: Location, val value: DataType) : Expr(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitTypeExpr(this)
    }
    
    class Array(loc: Location, val elements: List<Expr>) : Expr(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitArrayExpr(this)
    }
    
    class Unary(loc: Location, val op: Token.Type, val expr: Expr) : Expr(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitUnaryExpr(this)
    }
    
    class Binary(loc: Location, val op: Token.Type, val left: Expr, val right: Expr) : Expr(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitBinaryExpr(this)
    }
    
    class Get(loc: Location, val target: Expr, val index: Expr) : Expr(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitGetExpr(this)
    }
    
    class Set(loc: Location, val target: Expr, val index: Expr, val expr: Expr) : Expr(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitSetExpr(this)
    }
    
    class Invoke(loc: Location, val name: Name, val args: List<Expr>) : Expr(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitInvokeExpr(this)
    }
    
    class Instantiate(loc: Location, val target: Name, val elements: List<Expr>) : Expr(loc) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitInstantiateExpr(this)
    }
}

fun Any.toExpr(location: Location = Location.none) =
    Expr.Value(location, this)

fun DataType.toType() =
    Expr.Type(Location.none, this)

fun String.toName() =
    Expr.Name(Location.none, this)