package kakkoiichris.kb.runtime

import kakkoiichris.kb.lexer.Context
import kakkoiichris.kb.parser.Expr
import kakkoiichris.kb.parser.toExpr
import kakkoiichris.kb.parser.toName
import kakkoiichris.kb.util.KBError
import kotlin.reflect.KClass

interface DataType {
    companion object {
        fun infer(runtime: Runtime, x: Any?): DataType =
            when (x) {
                KBEmpty    -> Primitive.ANY

                is KBArray -> x.value.type.array

                is KBEnum  -> Enum(x.value.type.toName())

                is KBData  -> Data(x.value.name)

                is List<*> -> {
                    val xn = x.filterNotNull()

                    when {
                        xn.isEmpty()                   -> Primitive.ANY.array

                        xn.isHomogenous(runtime)       -> Array(infer(runtime, KBValue.of(xn[0])))

                        xn.all { it is ArrayInstance } -> Primitive.ANY.array.array

                        else                           -> Primitive.ANY.array
                    }
                }

                is KBV     -> Primitive.infer(runtime, x)

                else       -> TODO("NOT KBV")
            }

        private fun List<*>.isHomogenous(runtime: Runtime): Boolean {
            if (isEmpty()) return true

            val firstType = infer(runtime, KBValue.of(get(0)!!))

            for (x in drop(1)) {
                if (firstType.filter(runtime, KBValue.of(x!!)) == null) {
                    return false
                }
            }

            return true
        }

        fun resolve(runtime: Runtime, type: DataType): DataType {
            if (type is Data) {
                val alias = runtime.memory.getAlias(type.name)

                if (alias != null) {
                    return alias
                }

                val enum = runtime.memory.getEnum(type.name)

                if (enum != null) {
                    return Enum(enum.name.toName())
                }
            }

            return type
        }
    }

    val iterableType: DataType? get() = null

    val vararg get() = Vararg(this)

    val array get() = Array(this)

    fun filter(runtime: Runtime, x: KBV?): KBV? = x.takeIf { it === KBEmpty }

    fun cast(runtime: Runtime, x: KBV?): KBV? = null

    fun coerce(x: KBV?): KBV? = x

    fun iterable(runtime: Runtime, x: KBV?): List<KBV>? = null

    fun default(runtime: Runtime): KBV? = null

    object Inferred : DataType {
        override fun coerce(x: KBV?) = null

        override fun filter(runtime: Runtime, x: KBV?) = x
    }

    enum class Primitive(private val clazz: KClass<out Any>) : DataType {
        NONE(Unit::class) {
            override fun default(runtime: Runtime) = KBEmpty
        },

        BOOL(Boolean::class) {
            override fun cast(runtime: Runtime, x: KBV?) = when (x) {
                is KBBool   -> x
                is KBByte   -> KBBool(x.value != 0.toByte())
                is KBShort  -> KBBool(x.value != 0.toShort())
                is KBInt    -> KBBool(x.value != 0)
                is KBLong   -> KBBool(x.value != 0L)
                is KBFloat  -> KBBool(x.value != 0F)
                is KBDouble -> KBBool(x.value != 0.0)
                is KBChar   -> KBBool(x.value != '\u0000')
                is KBString -> KBBool(x.value.toBoolean())
                else        -> null
            }

            override fun default(runtime: Runtime) = KBBool(false)
        },

        BYTE(Byte::class) {
            override fun cast(runtime: Runtime, x: KBV?) = when (x) {
                is KBBool   -> KBByte((if (x.value) 0 else 1).toByte())
                is KBByte   -> x
                is KBShort  -> KBByte(x.value.toByte())
                is KBInt    -> KBByte(x.value.toByte())
                is KBLong   -> KBByte(x.value.toByte())
                is KBFloat  -> KBByte(x.value.toInt().toByte())
                is KBDouble -> KBByte(x.value.toInt().toByte())
                is KBChar   -> KBByte(x.value.code.toByte())
                is KBString -> KBByte(x.value.toByte())
                else        -> null
            }

            override fun coerce(x: KBV?) = when (x) {
                is KBByte -> x
                else      -> null
            }

            override fun default(runtime: Runtime) = KBByte(0.toByte())
        },

        SHORT(Short::class) {
            override fun cast(runtime: Runtime, x: KBV?) = when (x) {
                is KBBool   -> KBShort((if (x.value) 0 else 1).toShort())
                is KBByte   -> KBShort(x.value.toShort())
                is KBShort  -> x
                is KBInt    -> KBShort(x.value.toShort())
                is KBLong   -> KBShort(x.value.toShort())
                is KBFloat  -> KBShort(x.value.toInt().toShort())
                is KBDouble -> KBShort(x.value.toInt().toShort())
                is KBChar   -> KBShort(x.value.code.toShort())
                is KBString -> KBShort(x.value.toShort())
                else        -> null
            }

            override fun coerce(x: KBV?) = when (x) {
                is KBByte  -> KBShort(x.value.toShort())
                is KBShort -> x
                else       -> null
            }

            override fun default(runtime: Runtime) = KBShort(0.toShort())
        },

        INT(Int::class) {
            override fun cast(runtime: Runtime, x: KBV?) = when (x) {
                is KBBool   -> KBInt(if (x.value) 0 else 1)
                is KBByte   -> KBInt(x.value.toInt())
                is KBShort  -> KBInt(x.value.toInt())
                is KBInt    -> x
                is KBLong   -> KBInt(x.value.toInt())
                is KBFloat  -> KBInt(x.value.toInt())
                is KBDouble -> KBInt(x.value.toInt())
                is KBChar   -> KBInt(x.value.code)
                is KBString -> KBInt(x.value.toInt())
                else        -> null
            }

            override fun coerce(x: KBV?) = when (x) {
                is KBByte  -> KBInt(x.value.toInt())
                is KBShort -> KBInt(x.value.toInt())
                is KBInt   -> KBInt(x.value)
                is KBChar  -> KBInt(x.value.code)
                else       -> null
            }

            override fun default(runtime: Runtime) = KBInt(0)
        },

        LONG(Long::class) {
            override fun cast(runtime: Runtime, x: KBV?) = when (x) {
                is KBBool   -> KBLong(if (x.value) 0L else 1L)
                is KBByte   -> KBLong(x.value.toLong())
                is KBShort  -> KBLong(x.value.toLong())
                is KBInt    -> KBLong(x.value.toLong())
                is KBLong   -> x
                is KBFloat  -> KBLong(x.value.toLong())
                is KBDouble -> KBLong(x.value.toLong())
                is KBChar   -> KBLong(x.value.code.toLong())
                is KBString -> KBLong(x.value.toLong())
                else        -> null
            }

            override fun coerce(x: KBV?) = when (x) {
                is KBByte  -> KBLong(x.value.toLong())
                is KBShort -> KBLong(x.value.toLong())
                is KBInt   -> KBLong(x.value.toLong())
                is KBLong  -> KBLong(x.value)
                is KBChar  -> KBLong(x.value.code.toLong())
                else       -> null
            }

            override fun default(runtime: Runtime) = KBLong(0L)
        },

        FLOAT(Float::class) {
            override fun cast(runtime: Runtime, x: KBV?) = when (x) {
                is KBBool   -> KBFloat(if (x.value) 0F else 1F)
                is KBByte   -> KBFloat(x.value.toFloat())
                is KBShort  -> KBFloat(x.value.toFloat())
                is KBInt    -> KBFloat(x.value.toFloat())
                is KBLong   -> KBFloat(x.value.toFloat())
                is KBFloat  -> x
                is KBDouble -> KBFloat(x.value.toFloat())
                is KBChar   -> KBFloat(x.value.code.toFloat())
                is KBString -> KBFloat(x.value.toFloat())
                else        -> null
            }

            override fun coerce(x: KBV?) = when (x) {
                is KBByte  -> KBFloat(x.value.toFloat())
                is KBShort -> KBFloat(x.value.toFloat())
                is KBInt   -> KBFloat(x.value.toFloat())
                is KBLong  -> KBFloat(x.value.toFloat())
                is KBFloat -> KBFloat(x.value)
                is KBChar  -> KBFloat(x.value.code.toFloat())
                else       -> null
            }

            override fun default(runtime: Runtime) = KBFloat(0F)
        },

        DOUBLE(Double::class) {
            override fun cast(runtime: Runtime, x: KBV?) = when (x) {
                is KBBool   -> KBDouble(if (x.value) 0.0 else 1.0)
                is KBByte   -> KBDouble(x.value.toDouble())
                is KBShort  -> KBDouble(x.value.toDouble())
                is KBInt    -> KBDouble(x.value.toDouble())
                is KBLong   -> KBDouble(x.value.toDouble())
                is KBFloat  -> KBDouble(x.value.toDouble())
                is KBDouble -> x
                is KBChar   -> KBDouble(x.value.code.toDouble())
                is KBString -> KBDouble(x.value.toDouble())
                else        -> null
            }

            override fun coerce(x: KBV?) = when (x) {
                is KBByte   -> KBDouble(x.value.toDouble())
                is KBShort  -> KBDouble(x.value.toDouble())
                is KBInt    -> KBDouble(x.value.toDouble())
                is KBLong   -> KBDouble(x.value.toDouble())
                is KBFloat  -> KBDouble(x.value.toDouble())
                is KBDouble -> KBDouble(x.value)
                is KBChar   -> KBDouble(x.value.code.toDouble())
                else        -> null
            }

            override fun default(runtime: Runtime) = KBDouble(0.0)
        },

        CHAR(Char::class) {
            override fun cast(runtime: Runtime, x: KBV?) = when (x) {
                is KBByte   -> KBChar(x.value.toInt().toChar())
                is KBShort  -> KBChar(x.value.toInt().toChar())
                is KBInt    -> KBChar(x.value.toChar())
                is KBLong   -> KBChar(x.value.toInt().toChar())
                is KBFloat  -> KBChar(x.value.toInt().toChar())
                is KBDouble -> KBChar(x.value.toInt().toChar())
                is KBChar   -> x
                else        -> null
            }

            override fun default(runtime: Runtime) = KBChar('\u0000')
        },

        STRING(String::class) {
            override val iterableType: DataType get() = CHAR

            override fun cast(runtime: Runtime, x: KBV?) = when (x) {
                null -> null
                else -> KBString(x.value.toString())
            }

            override fun default(runtime: Runtime) = KBString("")

            override fun iterable(runtime: Runtime, x: KBV?): List<KBV>? =
                cast(runtime, x)?.value?.toCharArray()?.map { KBChar(it) }
        },

        ANY(Any::class) {
            override fun cast(runtime: Runtime, x: KBV?) = x

            override fun default(runtime: Runtime) = KBEmpty
        };

        companion object {
            fun infer(runtime: Runtime, x: KBV?) =
                values().first { it.filter(runtime, x) != null }
        }

        override fun filter(runtime: Runtime, x: KBV?): KBV? =
            super.filter(runtime, x) ?: x.takeIf { clazz.isInstance(x) }

        override fun toString() = name.lowercase()
    }

    class Array(val subType: DataType, val initSize: Expr? = null) : DataType {
        override val iterableType: DataType get() = subType

        override fun filter(runtime: Runtime, x: KBV?): KBV? {
            val match = super.filter(runtime, x)

            if (match != null) {
                return match
            }

            if (x !is KBArray) {
                return null
            }

            val initSize = if (initSize != null) {
                val value = runtime.visit(initSize)

                Primitive.INT.coerce(value) as? KBInt ?: KBError.invalidArraySize()
            }
            else KBInt(0)

            if (initSize.value > 0 && x.value.size != initSize.value) {
                return null
            }

            return x.takeIf { it.value.all { e -> subType.filter(runtime, e) != null } }
        }

        override fun cast(runtime: Runtime, x: KBV?) = when (x) {
            is KBString -> when (subType) {
                Primitive.CHAR -> KBArray(ArrayInstance(subType, x.value.map { KBChar(it) }.toMutableList()))

                else           -> null
            }

            is KBArray  -> if (x.value.type == subType)
                x
            else
                KBArray(ArrayInstance(subType, x.value.map {
                    subType.cast(runtime, it) ?: return null
                }.toMutableList()))

            else        -> null
        }

        override fun iterable(runtime: Runtime, x: KBV?) = cast(runtime, x)?.value

        override fun default(runtime: Runtime): KBArray {
            val initSize = if (initSize != null) {
                val value = runtime.visit(initSize)

                Primitive.INT.coerce(value) as? KBInt ?: KBError.invalidArraySize()
            }
            else KBInt(0)

            return KBArray(ArrayInstance(subType, MutableList(initSize.value) {
                subType.default(runtime) ?: KBError.noDefaultValue(Primitive.ANY.array, Context.none)
            }))
        }

        override fun toString() = "$subType[]"

        fun mismatchedSize(runtime: Runtime, x: KBV?): Boolean {
            if (x !is KBArray) {
                return false
            }

            val initSize = if (initSize != null) {
                val value = runtime.visit(initSize)

                Primitive.INT.coerce(value) as? KBInt ?: KBError.invalidArraySize()
            }
            else KBInt(0)

            return initSize.value > 0 && x.value.size != initSize.value
        }
    }

    class Vararg(private val subType: DataType) : DataType {
        private val arrayType = Array(subType, 0.toExpr())

        override val iterableType: DataType get() = subType

        override fun filter(runtime: Runtime, x: KBV?): KBV? {
            val match = super.filter(runtime, x)

            if (match != null) {
                return match
            }

            if (x !is KBArray) {
                return null
            }

            return x.takeIf { it.value.all { e -> subType.filter(runtime, e) != null } }
        }

        override fun default(runtime: Runtime) = arrayType.default(runtime)

        override fun toString() = "$subType*"
    }

    class Data(val name: Expr.Name) : DataType {
        override val iterableType: DataType get() = Primitive.ANY

        override fun filter(runtime: Runtime, x: KBV?): KBV? =
            super.filter(runtime, x) ?: x.takeIf { it is KBData && it.value.name == name }

        override fun cast(runtime: Runtime, x: KBV?): KBV? =
            (x as? KBData)?.takeIf { it.value.name == name }

        override fun coerce(x: KBV?) = x.takeIf { it is KBData && it.value.name == name }

        override fun iterable(runtime: Runtime, x: KBV?): List<KBV>? = (x as? KBData)?.value?.deref()

        override fun default(runtime: Runtime): KBV {
            val data = runtime.memory.getData(name) ?: KBError.undeclaredData(name, Context.none)

            val scope = Memory.Scope(name.value, runtime.memory.peek())

            try {
                runtime.memory.push(scope)

                for (decl in data.decls) {
                    runtime.visit(decl)
                }
            }
            finally {
                runtime.memory.pop()
            }

            return KBData(DataInstance(name, scope))
        }

        override fun toString() = name.value
    }

    class Enum(val name: Expr.Name) : DataType {
        override fun filter(runtime: Runtime, x: KBV?): KBV? =
            super.filter(runtime, x) ?: x.takeIf { it is KBEnum && it.value.type == name.value }

        override fun cast(runtime: Runtime, x: KBV?): KBV? =
            (x as? KBEnum)?.takeIf { it.value.type == name.value }

        override fun coerce(x: KBV?) = x.takeIf { it is KBEnum && it.value.type == name.value }

        override fun default(runtime: Runtime): KBV {
            val enum = runtime.memory.getEnum(name) ?: KBError.undeclaredData(name, Context.none)

            return KBEnum(enum[0])
        }

        override fun toString() = name.value
    }
}

val String.data
    get() = DataType.Data(lowercase().toName())