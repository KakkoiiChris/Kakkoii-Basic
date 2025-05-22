package kakkoiichris.kb.parser

import kakkoiichris.kb.lexer.Context
import kakkoiichris.kb.lexer.Token
import kakkoiichris.kb.runtime.*

sealed class Expr(val context: Context) {
    open fun getDataType(): DataType =
        DataType.Primitive.NONE

    abstract fun <X> accept(visitor: Visitor<X>): X

    interface Visitor<X> {
        fun visit(expr: Expr) =
            expr.accept(this)

        fun visitEmptyExpr(expr: Empty): X

        fun visitValueExpr(expr: Value): X

        fun visitNameExpr(expr: Name): X

        fun visitVariableExpr(expr: Variable): X

        fun visitArrayExpr(expr: Array): X

        fun visitUnaryExpr(expr: Unary): X

        fun visitBinaryExpr(expr: Binary): X

        fun visitAssignExpr(expr: Assign): X

        fun visitTypeCheckExpr(expr: TypeCheck): X

        fun visitTypeCastExpr(expr: TypeCast): X

        fun visitGetIndexExpr(expr: GetIndex): X

        fun visitSetIndexExpr(expr: SetIndex): X

        fun visitGetMemberExpr(expr: GetMember): X

        fun visitSetMemberExpr(expr: SetMember): X

        fun visitGetEntryExpr(expr: GetEntry): X

        fun visitInvokeExpr(expr: Invoke): X

        fun visitInstantiateExpr(expr: Instantiate): X
    }

    data object Empty : Expr(Context.none) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitEmptyExpr(this)
    }

    class Value(context: Context, val value: KBV) : Expr(context) {
        override fun getDataType() = when (value) {
            KBNone      -> DataType.Primitive.NONE
            is KBBool   -> DataType.Primitive.BOOL
            is KBByte   -> DataType.Primitive.BYTE
            is KBShort  -> DataType.Primitive.SHORT
            is KBInt    -> DataType.Primitive.INT
            is KBLong   -> DataType.Primitive.LONG
            is KBFloat  -> DataType.Primitive.FLOAT
            is KBDouble -> DataType.Primitive.DOUBLE
            is KBChar   -> DataType.Primitive.CHAR
            is KBString -> DataType.Primitive.STRING
            else        -> TODO("VALUE DATA TYPE")
        }

        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitValueExpr(this)
    }

    class Name(context: Context, val value: String) : Expr(context) {
        companion object {
            val none get() = Name(Context.none, "")
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

    class Variable(context: Context, val value: String, val type: DataType) : Expr(context) {
        companion object {
            val none get() = Variable(Context.none, "", DataType.Primitive.NONE)
        }

        override fun getDataType() = type

        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitVariableExpr(this)

        override fun toString() =
            value

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Variable

            if (value != other.value) return false
            if (type != other.type) return false

            return true
        }

        override fun hashCode(): Int {
            var result = value.hashCode()
            result = 31 * result + type.hashCode()
            return result
        }
    }

    class Type(val context: Context, val value: DataType) {
        companion object {
            val none get() = Type(Context.none, DataType.Inferred)
        }
    }

    class Array(context: Context, val elements: List<Element>) : Expr(context) {
        override fun getDataType(): DataType {
            if (elements.isEmpty()) return DataType.Primitive.NONE

            val firstType = elements.first().expr.getDataType()

            if (elements.any { it.expr.getDataType() != firstType }) {
                return DataType.Primitive.ANY.array
            }

            return firstType.array
        }

        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitArrayExpr(this)

        data class Element(val expr: Expr, val each: Boolean)
    }

    class Unary(context: Context, val op: Operator, val expr: Expr) : Expr(context) {
        enum class Operator(private val type: Token.Type) {
            NEGATE(Token.Symbol.DASH),
            NOT(Token.Keyword.NOT),
            LENGTH(Token.Symbol.POUND),
            STRING(Token.Symbol.DOLLAR),
            VALUE(Token.Symbol.AT);

            companion object {
                operator fun get(type: Token.Type) =
                    values().first { it.type == type }
            }

            override fun toString() = type.toString()
        }

        override fun getDataType() = when (op) {
            Operator.NEGATE -> when (val type = expr.getDataType()) {
                DataType.Primitive.BYTE,
                DataType.Primitive.SHORT,
                DataType.Primitive.INT,
                DataType.Primitive.LONG,
                DataType.Primitive.FLOAT,
                DataType.Primitive.DOUBLE,
                DataType.Primitive.STRING -> type

                else                      -> TODO()
            }

            Operator.NOT    -> when (val type = expr.getDataType()) {
                DataType.Primitive.BOOL -> type

                else                    -> TODO()
            }

            Operator.LENGTH -> DataType.Primitive.INT

            Operator.STRING -> DataType.Primitive.STRING

            Operator.VALUE  -> TODO()
        }

        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitUnaryExpr(this)
    }

    class Binary(context: Context, val op: Operator, val left: Expr, val right: Expr) : Expr(context) {
        enum class Operator(private val type: Token.Type) {
            OR(Token.Keyword.OR),
            AND(Token.Keyword.AND),
            EQUAL(Token.Symbol.DOUBLE_EQUAL),
            NOT_EQUAL(Token.Symbol.LESS_GREATER),
            LESS(Token.Symbol.LESS_SIGN),
            LESS_EQUAL(Token.Symbol.LESS_EQUAL_SIGN),
            GREATER(Token.Symbol.GREATER_SIGN),
            GREATER_EQUAL(Token.Symbol.GREATER_EQUAL_SIGN),
            CONCAT(Token.Symbol.AMPERSAND),
            ADD(Token.Symbol.PLUS),
            SUBTRACT(Token.Symbol.DASH),
            MULTIPLY(Token.Symbol.STAR),
            DIVIDE(Token.Symbol.SLASH),
            MODULUS(Token.Symbol.PERCENT);

            companion object {
                operator fun get(type: Token.Type) =
                    values().first { it.type == type }
            }

            override fun toString() = type.toString()
        }

        override fun getDataType() = when (op) {
            Operator.OR            -> when (left.getDataType()) {
                DataType.Primitive.BOOL -> when (right.getDataType()) {
                    DataType.Primitive.BOOL -> DataType.Primitive.BOOL
                    else                    -> DataType.Primitive.NONE
                }

                else                    -> DataType.Primitive.NONE
            }

            Operator.AND           -> when (left.getDataType()) {
                DataType.Primitive.BOOL -> when (right.getDataType()) {
                    DataType.Primitive.BOOL -> DataType.Primitive.BOOL
                    else                    -> DataType.Primitive.NONE
                }

                else                    -> DataType.Primitive.NONE
            }

            Operator.EQUAL         -> DataType.Primitive.BOOL

            Operator.NOT_EQUAL     -> DataType.Primitive.BOOL

            Operator.LESS          -> DataType.Primitive.BOOL

            Operator.LESS_EQUAL    -> DataType.Primitive.BOOL

            Operator.GREATER       -> DataType.Primitive.BOOL

            Operator.GREATER_EQUAL -> DataType.Primitive.BOOL

            Operator.CONCAT        -> DataType.Primitive.STRING

            Operator.ADD           -> when (left.getDataType()) {
                DataType.Primitive.FLOAT -> DataType.Primitive.FLOAT

                DataType.Primitive.INT   -> DataType.Primitive.INT

                else                     -> DataType.Primitive.NONE
            }

            Operator.SUBTRACT      -> when (left.getDataType()) {
                DataType.Primitive.FLOAT -> DataType.Primitive.FLOAT

                DataType.Primitive.INT   -> DataType.Primitive.INT

                else                     -> DataType.Primitive.NONE
            }

            Operator.MULTIPLY      -> when (left.getDataType()) {
                DataType.Primitive.FLOAT -> DataType.Primitive.FLOAT

                DataType.Primitive.INT   -> DataType.Primitive.INT

                else                     -> DataType.Primitive.NONE
            }

            Operator.DIVIDE        -> when (left.getDataType()) {
                DataType.Primitive.FLOAT -> DataType.Primitive.FLOAT

                DataType.Primitive.INT   -> DataType.Primitive.INT

                else                     -> DataType.Primitive.NONE
            }

            Operator.MODULUS       -> when (left.getDataType()) {
                DataType.Primitive.FLOAT -> DataType.Primitive.FLOAT

                DataType.Primitive.INT   -> DataType.Primitive.INT

                else                     -> DataType.Primitive.NONE
            }
        }

        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitBinaryExpr(this)
    }

    class Assign(context: Context, val name: Name, val value: Expr) : Expr(context) {
        override fun getDataType() =
            value.getDataType()

        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitAssignExpr(this)
    }

    class TypeCheck(context: Context, val value: Expr, val type: Type, val invert: Boolean) : Expr(context) {
        override fun getDataType() =
            DataType.Primitive.BOOL

        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitTypeCheckExpr(this)
    }

    class TypeCast(context: Context, val value: Expr, val type: Type) : Expr(context) {
        override fun getDataType() =
            type.value

        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitTypeCastExpr(this)
    }

    class GetIndex(context: Context, val target: Expr, val index: Expr) : Expr(context) {
        override fun getDataType() = when (val t = target.getDataType()) {
            DataType.Primitive.STRING -> DataType.Primitive.CHAR

            is DataType.Array         -> t.subType

            else                      -> DataType.Primitive.NONE
        }

        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitGetIndexExpr(this)
    }

    class SetIndex(context: Context, val target: Expr, val index: Expr, val expr: Expr) : Expr(context) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitSetIndexExpr(this)
    }

    class GetMember(context: Context, val target: Expr, val member: Name) : Expr(context) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitGetMemberExpr(this)
    }

    class SetMember(context: Context, val target: Expr, val member: Name, val expr: Expr) : Expr(context) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitSetMemberExpr(this)
    }

    class GetEntry(context: Context, val target: Name, val member: Name) : Expr(context) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitGetEntryExpr(this)
    }

    class Invoke(context: Context, val name: Name, val args: List<Argument>) : Expr(context) {
        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitInvokeExpr(this)

        data class Argument(val each: Boolean, val expr: Expr)
    }

    class Instantiate(context: Context, val target: Name, val elements: List<Expr>) : Expr(context) {
        val isInferred get() = target == Name.none

        fun withTarget(target: Name) =
            Instantiate(context, target, elements)

        override fun <X> accept(visitor: Visitor<X>): X =
            visitor.visitInstantiateExpr(this)
    }
}

fun Any.toExpr(context: Context = Context.none) =
    Expr.Value(context, KBValue.of(this)!!)

fun DataType.toType() =
    Expr.Type(Context.none, this)

fun String.toName() =
    Expr.Name(Context.none, this)