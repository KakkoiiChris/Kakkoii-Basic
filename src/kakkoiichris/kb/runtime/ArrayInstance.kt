package kakkoiichris.kb.runtime

class ArrayInstance(val type: DataType, private val elements: MutableList<Any>) : MutableList<Any> by elements {
    fun asBooleanArray() =
        map { it as Boolean }.toBooleanArray()
    
    fun asByteArray() =
        map { it as Byte }.toByteArray()
    
    fun asShortArray() =
        map { it as Short }.toShortArray()
    
    fun asIntArray() =
        map { it as Int }.toIntArray()
    
    fun asLongArray() =
        map { it as Long }.toLongArray()
    
    fun asFloatArray() =
        map { it as Float }.toFloatArray()
    
    fun asDoubleArray() =
        map { it as Double }.toDoubleArray()
    
    fun asCharArray() =
        map { it as Char }.toCharArray()
    
    fun asStringArray() =
        map { it as String }.toTypedArray()
    
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        
        if (other !is ArrayInstance) return false
        
        if (size != other.size) return false
        
        for (i in 0 until size) {
            if (get(i) != other[i]) return false
        }
        
        return true
    }
    
    override fun toString() =
        if (isEmpty())
            "[]"
        else
            joinToString(prefix = "[ ", postfix = " ]")
    
    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + elements.hashCode()
        return result
    }
}

fun BooleanArray.toArrayInstance() =
    ArrayInstance(DataType.Primitive.BOOL, this.toList().toMutableList())

fun ByteArray.toArrayInstance() =
    ArrayInstance(DataType.Primitive.BYTE, this.toList().toMutableList())

fun ShortArray.toArrayInstance() =
    ArrayInstance(DataType.Primitive.SHORT, this.toList().toMutableList())

fun IntArray.toArrayInstance() =
    ArrayInstance(DataType.Primitive.INT, this.toList().toMutableList())

fun LongArray.toArrayInstance() =
    ArrayInstance(DataType.Primitive.LONG, this.toList().toMutableList())

fun FloatArray.toArrayInstance() =
    ArrayInstance(DataType.Primitive.FLOAT, this.toList().toMutableList())

fun DoubleArray.toArrayInstance() =
    ArrayInstance(DataType.Primitive.DOUBLE, this.toList().toMutableList())

fun CharArray.toArrayInstance() =
    ArrayInstance(DataType.Primitive.CHAR, this.toList().toMutableList())

fun Array<String>.toArrayInstance() =
    ArrayInstance(DataType.Primitive.STRING, this.toList().toMutableList())