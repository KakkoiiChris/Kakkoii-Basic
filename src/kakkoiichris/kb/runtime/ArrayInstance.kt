package kakkoiichris.kb.runtime

@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
class ArrayInstance(val type: DataType, private val elements: MutableList<KBV>) : MutableList<KBV> by elements {
    fun asBooleanArray() =
        map { (it as KBBool).value }.toBooleanArray()

    fun asByteArray() =
        map { (it as KBByte).value }.toByteArray()

    fun asShortArray() =
        map { (it as KBShort).value }.toShortArray()

    fun asIntArray() =
        map { (it as KBInt).value }.toIntArray()

    fun asLongArray() =
        map { (it as KBLong).value }.toLongArray()

    fun asFloatArray() =
        map { (it as KBFloat).value }.toFloatArray()

    fun asDoubleArray() =
        map { (it as KBDouble).value }.toDoubleArray()

    fun asCharArray() =
        map { (it as KBChar).value }.toCharArray()

    fun asStringArray() =
        map { (it as KBString).value }.toTypedArray()

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
    ArrayInstance(DataType.Primitive.BOOL, this.map(::KBBool).toMutableList())

fun ByteArray.toArrayInstance() =
    ArrayInstance(DataType.Primitive.BYTE, this.map(::KBByte).toMutableList())

fun ShortArray.toArrayInstance() =
    ArrayInstance(DataType.Primitive.SHORT, this.map(::KBShort).toMutableList())

fun IntArray.toArrayInstance() =
    ArrayInstance(DataType.Primitive.INT, this.map(::KBInt).toMutableList())

fun LongArray.toArrayInstance() =
    ArrayInstance(DataType.Primitive.LONG, this.map(::KBLong).toMutableList())

fun FloatArray.toArrayInstance() =
    ArrayInstance(DataType.Primitive.FLOAT, this.map(::KBFloat).toMutableList())

fun DoubleArray.toArrayInstance() =
    ArrayInstance(DataType.Primitive.DOUBLE, this.map(::KBDouble).toMutableList())

fun CharArray.toArrayInstance() =
    ArrayInstance(DataType.Primitive.CHAR, this.map(::KBChar).toMutableList())

fun Array<String>.toArrayInstance() =
    ArrayInstance(DataType.Primitive.STRING, this.map(::KBString).toMutableList())