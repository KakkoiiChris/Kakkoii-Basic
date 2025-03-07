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
                Empty                 -> Primitive.ANY

                is ArrayInstance      -> x.type.array

                is EnumInstance.Entry -> Enum(x.type.toName())

                is DataInstance       -> Data(x.name)

                is List<*>            -> {
                    val xn = x.filterNotNull()

                    when {
                        xn.isEmpty()                   -> Primitive.ANY.array

                        xn.isHomogenous(runtime) -> Array(infer(runtime, xn[0]))

                        xn.all { it is ArrayInstance } -> Primitive.ANY.array.array

                        else                           -> Primitive.ANY.array
                    }
                }

                else                  -> Primitive.infer(runtime, x)
            }

        private fun List<*>.isHomogenous(runtime: Runtime): Boolean {
            val firstType = infer(runtime, get(0))

            for (x in drop(1)) {
                if (firstType.filter(runtime, x) == null) {
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

    fun filter(runtime: Runtime, x: Any?): Any? = x.takeIf { it === Empty }

    fun cast(runtime: Runtime, x: Any?): Any? = null

    fun coerce(x: Any?): Any? = x

    fun iterable(runtime: Runtime, x: Any?): List<Any>? = null

    fun default(runtime: Runtime): Any? = null

    object Inferred : DataType {
        override fun coerce(x: Any?) = null

        override fun filter(runtime: Runtime, x: Any?) = x
    }

    enum class Primitive(private val clazz: KClass<out Any>) : DataType {
        NONE(Unit::class) {
            override fun default(runtime: Runtime) = Unit
        },

        BOOL(Boolean::class) {
            override fun cast(runtime: Runtime, x: Any?) = when (x) {
                is Boolean -> x
                is Byte    -> x != 0.toByte()
                is Short   -> x != 0.toShort()
                is Int     -> x != 0
                is Long    -> x != 0L
                is Float   -> x != 0F
                is Double  -> x != 0.0
                is Char    -> x != '\u0000'
                is String  -> x.toBoolean()
                else       -> null
            }

            override fun default(runtime: Runtime) = false
        },

        BYTE(Byte::class) {
            override fun cast(runtime: Runtime, x: Any?) = when (x) {
                is Boolean -> (if (x) 0 else 1).toByte()
                is Byte    -> x
                is Short   -> x.toByte()
                is Int     -> x.toByte()
                is Long    -> x.toByte()
                is Float   -> x.toInt().toByte()
                is Double  -> x.toInt().toByte()
                is Char    -> x.code.toByte()
                is String  -> x.toByte()
                else       -> null
            }

            override fun coerce(x: Any?) = when (x) {
                is Byte -> x
                else    -> null
            }

            override fun default(runtime: Runtime) = 0.toByte()
        },

        SHORT(Short::class) {
            override fun cast(runtime: Runtime, x: Any?) = when (x) {
                is Boolean -> (if (x) 0 else 1).toShort()
                is Byte    -> x.toShort()
                is Short   -> x
                is Int     -> x.toShort()
                is Long    -> x.toShort()
                is Float   -> x.toInt().toShort()
                is Double  -> x.toInt().toShort()
                is Char    -> x.code.toShort()
                is String  -> x.toShort()
                else       -> null
            }

            override fun coerce(x: Any?) = when (x) {
                is Byte  -> x.toShort()
                is Short -> x
                else     -> null
            }

            override fun default(runtime: Runtime) = 0.toShort()
        },

        INT(Int::class) {
            override fun cast(runtime: Runtime, x: Any?) = when (x) {
                is Boolean -> if (x) 0 else 1
                is Byte    -> x.toInt()
                is Short   -> x.toInt()
                is Int     -> x
                is Long    -> x.toInt()
                is Float   -> x.toInt()
                is Double  -> x.toInt()
                is Char    -> x.code
                is String  -> x.toInt()
                else       -> null
            }

            override fun coerce(x: Any?) = when (x) {
                is Byte  -> x.toInt()
                is Short -> x.toInt()
                is Int   -> x
                is Char  -> x.code
                else     -> null
            }

            override fun default(runtime: Runtime) = 0
        },

        LONG(Long::class) {
            override fun cast(runtime: Runtime, x: Any?) = when (x) {
                is Boolean -> if (x) 0L else 1L
                is Byte    -> x.toLong()
                is Short   -> x.toLong()
                is Int     -> x.toLong()
                is Long    -> x
                is Float   -> x.toLong()
                is Double  -> x.toLong()
                is Char    -> x.code.toLong()
                is String  -> x.toLong()
                else       -> null
            }

            override fun coerce(x: Any?) = when (x) {
                is Byte  -> x.toLong()
                is Short -> x.toLong()
                is Int   -> x.toLong()
                is Long  -> x
                is Char  -> x.code.toLong()
                else     -> null
            }

            override fun default(runtime: Runtime) = 0L
        },

        FLOAT(Float::class) {
            override fun cast(runtime: Runtime, x: Any?) = when (x) {
                is Boolean -> if (x) 0F else 1F
                is Byte    -> x.toFloat()
                is Short   -> x.toFloat()
                is Int     -> x.toFloat()
                is Long    -> x.toFloat()
                is Float   -> x
                is Double  -> x.toFloat()
                is Char    -> x.code.toFloat()
                is String  -> x.toFloat()
                else       -> null
            }

            override fun coerce(x: Any?) = when (x) {
                is Byte  -> x.toFloat()
                is Short -> x.toFloat()
                is Int   -> x.toFloat()
                is Long  -> x.toFloat()
                is Float -> x
                is Char  -> x.code.toFloat()
                else     -> null
            }

            override fun default(runtime: Runtime) = 0F
        },

        DOUBLE(Double::class) {
            override fun cast(runtime: Runtime, x: Any?) = when (x) {
                is Boolean -> if (x) 0.0 else 1.0
                is Byte    -> x.toDouble()
                is Short   -> x.toDouble()
                is Int     -> x.toDouble()
                is Long    -> x.toDouble()
                is Float   -> x.toDouble()
                is Double  -> x
                is Char    -> x.code.toDouble()
                is String  -> x.toDouble()
                else       -> null
            }

            override fun coerce(x: Any?) = when (x) {
                is Byte   -> x.toDouble()
                is Short  -> x.toDouble()
                is Int    -> x.toDouble()
                is Long   -> x.toDouble()
                is Float  -> x.toDouble()
                is Double -> x
                is Char   -> x.code.toDouble()
                else      -> null
            }

            override fun default(runtime: Runtime) = 0.0
        },

        CHAR(Char::class) {
            override fun cast(runtime: Runtime, x: Any?) = when (x) {
                is Byte   -> x.toInt().toChar()
                is Short  -> x.toInt().toChar()
                is Int    -> x.toChar()
                is Long   -> x.toInt().toChar()
                is Float  -> x.toInt().toChar()
                is Double -> x.toInt().toChar()
                is Char   -> x
                else      -> null
            }

            override fun default(runtime: Runtime) = '\u0000'
        },

        STRING(String::class) {
            override val iterableType: DataType get() = CHAR

            override fun cast(runtime: Runtime, x: Any?) = x?.toString()

            override fun default(runtime: Runtime) = ""

            override fun iterable(runtime: Runtime, x: Any?): List<Any>? = cast(runtime, x)?.toList()
        },

        ANY(Any::class) {
            override fun cast(runtime: Runtime, x: Any?) = x

            override fun default(runtime: Runtime): Any = Empty
        };

        companion object {
            fun infer(runtime: Runtime, x: Any?) =
                values().first { it.filter(runtime, x) != null }
        }

        override fun filter(runtime: Runtime, x: Any?): Any? =
            super.filter(runtime, x) ?: x.takeIf { clazz.isInstance(x) }

        override fun toString() = name.lowercase()
    }

    class Array(val subType: DataType, val initSize: Expr? = null) : DataType {
        override val iterableType: DataType get() = subType

        override fun filter(runtime: Runtime, x: Any?): Any? {
            val match = super.filter(runtime, x)

            if (match != null) {
                return match
            }

            if (x !is ArrayInstance) {
                return null
            }

            val initSize = if (initSize != null) {
                val value = runtime.visit(initSize).fromRef()

                Primitive.INT.coerce(value) as? Int ?: KBError.invalidArraySize()
            }
            else 0

            if (initSize > 0 && x.size != initSize) {
                return null
            }

            return x.takeIf { it.all { e -> subType.filter(runtime, e) != null } }
        }

        override fun cast(runtime: Runtime, x: Any?) = when (x) {
            is String        -> when (subType) {
                Primitive.CHAR -> ArrayInstance(subType, x.toList().toMutableList())

                else           -> null
            }

            is ArrayInstance -> if (x.type == subType)
                x
            else
                ArrayInstance(subType, x.map {
                    subType.cast(runtime, it) ?: return null
                }.toMutableList())

            else             -> null
        }

        override fun iterable(runtime: Runtime, x: Any?) = cast(runtime, x)

        override fun default(runtime: Runtime): ArrayInstance {
            val initSize = if (initSize != null) {
                val value = runtime.visit(initSize).fromRef()

                Primitive.INT.coerce(value) as? Int ?: KBError.invalidArraySize()
            }
            else 0

            return ArrayInstance(subType, MutableList(initSize) {
                subType.default(runtime) ?: KBError.noDefaultValue(DataType.Primitive.ANY.array, Context.none)
            })
        }

        override fun toString() = "$subType[]"

        fun mismatchedSize(runtime: Runtime, x: Any?): Boolean {
            if (x !is ArrayInstance) {
                return false
            }

            val initSize = if (initSize != null) {
                val value = runtime.visit(initSize).fromRef()

                Primitive.INT.coerce(value) as? Int ?: KBError.invalidArraySize()
            }
            else 0

            return initSize > 0 && x.size != initSize
        }
    }

    class Vararg(private val subType: DataType) : DataType {
        private val arrayType = Array(subType, 0.toExpr())

        override val iterableType: DataType get() = subType

        override fun filter(runtime: Runtime, x: Any?): Any? {
            val match = super.filter(runtime, x)

            if (match != null) {
                return match
            }

            if (x !is ArrayInstance) {
                return null
            }

            return x.takeIf { it.all { e -> subType.filter(runtime, e) != null } }
        }

        override fun default(runtime: Runtime) = arrayType.default(runtime)

        override fun toString() = "$subType*"
    }

    class Data(val name: Expr.Name) : DataType {
        override val iterableType: DataType get() = Primitive.ANY

        override fun filter(runtime: Runtime, x: Any?): Any? =
            super.filter(runtime, x) ?: x.takeIf { it is DataInstance && it.name == name }

        override fun cast(runtime: Runtime, x: Any?): Any? = (x as? DataInstance)?.takeIf { it.name == name }

        override fun coerce(x: Any?) = x.takeIf { it is DataInstance && it.name == name }

        override fun iterable(runtime: Runtime, x: Any?): List<Any>? = (x as? DataInstance)?.deref()

        override fun default(runtime: Runtime): Any {
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

            return DataInstance(name, scope)
        }

        override fun toString() = name.value
    }

    class Enum(val name: Expr.Name) : DataType {
        override fun filter(runtime: Runtime, x: Any?): Any? =
            super.filter(runtime, x) ?: x.takeIf { it is EnumInstance.Entry && it.type == name.value }

        override fun cast(runtime: Runtime, x: Any?): Any? = (x as? EnumInstance.Entry)?.takeIf { it.type == name.value }

        override fun coerce(x: Any?) = x.takeIf { it is EnumInstance.Entry && it.type == name.value }

        override fun default(runtime: Runtime): Any {
            val enum = runtime.memory.getEnum(name) ?: KBError.undeclaredData(name, Context.none)

            return enum[0]
        }

        override fun toString() = name.value
    }
}
val String.data
    get() = DataType.Data(lowercase().toName())