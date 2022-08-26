package kakkoiichris.kb.parser

import kakkoiichris.kb.lexer.Location
import kakkoiichris.kb.lexer.Token
import kakkoiichris.kb.script.DataType

sealed class Expr(val location: Location) {
    abstract fun <X> accept(visitor: Visitor<X>): X
    
    interface Visitor<X> {
        fun visit(expr: Expr) =
            expr.accept(this)
        
        fun visitEmptyExpr(expr: Empty): X
        
        fun visitValueExpr(expr: Value): X
        
        fun visitNameExpr(expr: Name): X
        
        fun visitTypeExpr(expr: Type): X
        
        fun visitArrayExpr(expr: Array): X
        
        fun visitUnaryExpr(expr: Unary): X
        
        fun visitBinaryExpr(expr: Binary): X
        
        fun visitGetIndexExpr(expr: GetIndex): X
        
        fun visitSetIndexExpr(expr: SetIndex): X
        
        fun visitGetMemberExpr(expr: GetMember): X
        
        fun visitSetMemberExpr(expr: SetMember): X
        
        fun visitInvokeExpr(expr: Invoke): X
        
        fun visitInstantiateExpr(expr: Instantiate): X
    }
    
    object Empty : Expr(Location.none) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitEmptyExpr(this)
    }
    
    class Value(location: Location, val value: Any) : Expr(location) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitValueExpr(this)
    }
    
    class Name(location: Location, val value: String) : Expr(location) {
        companion object {
            val none get() = Name(Location.none, "")
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
    
    class Type(location: Location, val value: DataType) : Expr(location) {
        companion object {
            val none get() = Type(Location.none, DataType.Inferred)
        }
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitTypeExpr(this)
    }
    
    class Array(location: Location, val elements: List<Expr>) : Expr(location) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitArrayExpr(this)
    }
    
    class Unary(location: Location, val op: Operator, val expr: Expr) : Expr(location) {
        enum class Operator(private val type: Token.Type) {
            NEGATE(Token.Type.DASH),
            NOT(Token.Type.NOT),
            LENGTH(Token.Type.POUND);
            
            companion object {
                operator fun get(type: Token.Type) =
                    values().first { it.type == type }
            }
            
            override fun toString() = type.toString()
        }
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitUnaryExpr(this)
    }
    
    class Binary(location: Location, val op: Operator, val left: Expr, val right: Expr) : Expr(location) {
        enum class Operator(private val type: Token.Type) {
            ASSIGN(Token.Type.EQUAL_SIGN),
            SWAP(Token.Type.DOLLAR),
            OR(Token.Type.OR),
            AND(Token.Type.AND),
            EQUAL(Token.Type.DOUBLE_EQUAL),
            NOT_EQUAL(Token.Type.LESS_GREATER),
            LESS(Token.Type.LESS_SIGN),
            LESS_EQUAL(Token.Type.LESS_EQUAL_SIGN),
            GREATER(Token.Type.GREATER_SIGN),
            GREATER_EQUAL(Token.Type.GREATER_EQUAL_SIGN),
            IS(Token.Type.IS),
            CONCAT(Token.Type.AMPERSAND),
            ADD(Token.Type.PLUS),
            SUBTRACT(Token.Type.DASH),
            MULTIPLY(Token.Type.STAR),
            DIVIDE(Token.Type.SLASH),
            MODULUS(Token.Type.PERCENT),
            AS(Token.Type.AS),
            DOT(Token.Type.DOT);
            
            companion object {
                operator fun get(type: Token.Type) =
                    values().first { it.type == type }
            }
            
            override fun toString() = type.toString()
        }
        
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitBinaryExpr(this)
    }
    
    class GetIndex(location: Location, val target: Expr, val index: Expr) : Expr(location) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitGetIndexExpr(this)
    }
    
    class SetIndex(location: Location, val target: Expr, val index: Expr, val expr: Expr) : Expr(location) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitSetIndexExpr(this)
    }
    
    class GetMember(location: Location, val target: Expr, val member: Name) : Expr(location) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitGetMemberExpr(this)
    }
    
    class SetMember(location: Location, val target: Expr, val member: Name, val expr: Expr) : Expr(location) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitSetMemberExpr(this)
    }
    
    class Invoke(location: Location, val name: Name, val args: List<Expr>) : Expr(location) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitInvokeExpr(this)
    }
    
    class Instantiate(location: Location, val target: Name, val elements: List<Expr>) : Expr(location) {
        val isInferred get() = target == Name.none
        
        fun withTarget(target: Name) =
            Instantiate(location, target, elements)
        
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