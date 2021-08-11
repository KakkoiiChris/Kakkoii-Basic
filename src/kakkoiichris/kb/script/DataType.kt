package kakkoiichris.kb.script

import kakkoiichris.kb.parser.Expr
import kakkoiichris.kb.parser.Stmt
import kakkoiichris.kb.parser.toExpr
import kakkoiichris.kb.parser.toName
import kotlin.reflect.KClass

interface DataType {
    companion object {
        fun infer(script: Script, x: Any?): DataType =
            when (x) {
                is ArrayInstance -> {
                    if (x.drop(1).all { x.first()::class.isInstance(it) }) {
                        Array(infer(script, x[0]))
                    }
                    else {
                        Array(Primitive.ANY)
                    }
                }
                
                is DataInstance  -> Data(x.name)
                
                is List<*>       -> {
                    val xn = x.filterNotNull()
                    
                    if (xn.isNotEmpty() && xn.drop(1).all { xn.first()::class.isInstance(it) }) {
                        Array(infer(script, xn[0]))
                    }
                    else {
                        Array(Primitive.ANY)
                    }
                }
                
                else             -> Primitive.infer(script, x)
            }
    }
    
    val iterableType: DataType?
    
    fun matches(script: Script, x: Any?): Any?
    
    fun cast(script: Script, x: Any?): Any?
    
    fun iterable(script: Script, x: Any?): List<Any>?
    
    fun default(script: Script): Any?
    
    fun getKClass(script: Script) = default(script)!!::class
    
    object Inferred : DataType {
        override val iterableType: DataType? get() = null
        
        override fun matches(script: Script, x: Any?) = x!!
        
        override fun cast(script: Script, x: Any?): Any? = null
        
        override fun iterable(script: Script, x: Any?): List<Any>? = null
        
        override fun default(script: Script): Any? = null
    }
    
    enum class Primitive(private val clazz: KClass<out Any>) : DataType {
        VOID(Unit::class) {
            override fun cast(script: Script, x: Any?): Any? = null
            
            override fun default(script: Script) = Unit
        },
        
        BOOL(Boolean::class) {
            override fun cast(script: Script, x: Any?) = when (x) {
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
            
            override fun default(script: Script) = false
        },
        
        BYTE(Byte::class) {
            override fun cast(script: Script, x: Any?) = when (x) {
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
            
            override fun default(script: Script) = 0.toByte()
        },
        
        SHORT(Short::class) {
            override fun cast(script: Script, x: Any?) = when (x) {
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
            
            override fun default(script: Script) = 0.toShort()
        },
        
        INT(Int::class) {
            override fun cast(script: Script, x: Any?) = when (x) {
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
            
            override fun default(script: Script) = 0
        },
        
        LONG(Long::class) {
            override fun cast(script: Script, x: Any?) = when (x) {
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
            
            override fun default(script: Script) = 0L
        },
        
        FLOAT(Float::class) {
            override fun cast(script: Script, x: Any?) = when (x) {
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
            
            override fun default(script: Script) = 0F
        },
        
        DOUBLE(Double::class) {
            override fun cast(script: Script, x: Any?) = when (x) {
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
            
            override fun default(script: Script) = 0.0
        },
        
        CHAR(Char::class) {
            override fun cast(script: Script, x: Any?) = when (x) {
                is Byte   -> x.toInt().toChar()
                is Short  -> x.toInt().toChar()
                is Int    -> x.toChar()
                is Long   -> x.toInt().toChar()
                is Float  -> x.toInt().toChar()
                is Double -> x.toInt().toChar()
                is Char   -> x
                else      -> null
            }
            
            override fun default(script: Script) = '\u0000'
        },
        
        STRING(String::class) {
            override val iterableType: DataType get() = CHAR
            
            override fun cast(script: Script, x: Any?) = x?.toString()
            
            override fun default(script: Script) = ""
            
            override fun iterable(script: Script, x: Any?): List<Any>? = cast(script, x)?.toList()
        },
        
        ANY(Any::class) {
            override fun cast(script: Script, x: Any?) = x
            
            override fun default(script: Script): Any? = null
        };
        
        companion object {
            fun infer(script: Script, x: Any?) =
                values().first { it.matches(script, x) != null }
        }
        
        override val iterableType: DataType? get() = null
        
        override fun matches(script: Script, x: Any?): Any? = x.takeIf { clazz.isInstance(x) }
        
        override fun iterable(script: Script, x: Any?): List<Any>? = null
        
        override fun toString() = name.lowercase()
    }
    
    class Array(val subType: DataType, val initSize: Expr = (-1).toExpr()) : DataType {
        override val iterableType: DataType get() = subType
        
        override fun matches(script: Script, x: Any?): Any? {
            if (x !is ArrayInstance) {
                return null
            }
            
            val initSize = script.visit(initSize).fromRef() as Int
            
            if (initSize > 0 && x.size != initSize) {
                return null
            }
            
            return x.takeIf { it.all { e -> subType.matches(script, e) != null } }
        }
        
        override fun cast(script: Script, x: Any?) = when (x) {
            is String        -> when (subType) {
                Primitive.CHAR -> ArrayInstance(subType, x.toMutableList().toMutableList())
                
                else           -> TODO()
            }
            
            is ArrayInstance -> ArrayInstance(subType, x.map { subType.cast(script, it) ?: TODO() }.toMutableList())
            
            else             -> TODO()
        }
        
        override fun iterable(script: Script, x: Any?) = cast(script, x)
        
        override fun default(script: Script): ArrayInstance {
            return ArrayInstance(subType,
                MutableList(script.visit(initSize).fromRef() as? Int ?: error("Invalid default array size!")) {
                    subType.default(script) ?: error("No default for any array!")
                })
        }
        
        override fun toString() = "$subType[]"
    }
    
    class Vararg(private val subType: DataType) : DataType {
        private val arrayType = Array(subType, 0.toExpr())
        
        override val iterableType: DataType get() = subType
        
        override fun matches(script: Script, x: Any?): Any? {
            if (x !is ArrayInstance) {
                return null
            }
            
            return x.takeIf { it.all { e -> subType.matches(script, e) != null } }
        }
        
        override fun cast(script: Script, x: Any?): Any? = null
        
        override fun iterable(script: Script, x: Any?): List<Any>? = null
        
        override fun default(script: Script) = arrayType.default(script)
        
        override fun toString() = "$subType*"
    }
    
    class Data(private val name: Expr.Name) : DataType {
        override val iterableType: DataType get() = Primitive.ANY
        
        override fun matches(script: Script, x: Any?): Any? = x.takeIf { it is DataInstance && it.name == name }
        
        override fun cast(script: Script, x: Any?): Any? = null
        
        override fun iterable(script: Script, x: Any?): List<Any>? = null
        
        override fun default(script: Script): Any {
            val data = script.memory.getRef(name)?.fromRef() as? Stmt.Data ?: TODO("Corrupted Data Default")
            
            val scope = Memory.Scope(name.value)
            
            try {
                script.memory.push(scope)
                
                for (decl in data.decls) {
                    script.visit(decl)
                }
            }
            finally {
                script.memory.pop()
            }
            
            return DataInstance(name, scope)
        }
        
        override fun toString() = name.value
    }
}

fun DataType.vararg() =
    DataType.Vararg(this)

fun DataType.array() =
    DataType.Array(this)

fun String.data() =
    DataType.Data(toName())