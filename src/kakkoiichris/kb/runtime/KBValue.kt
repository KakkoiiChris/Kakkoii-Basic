package kakkoiichris.kb.runtime

typealias KBV = KBValue<*>

sealed interface KBValue<X> {
    val value: X

    companion object {
        fun of(x: Any): KBV? = when (x) {
            is KBV -> x
            Unit          -> KBNone
            is Boolean    -> KBBool(x)
            is Byte       -> KBByte(x)
            is Short      -> KBShort(x)
            is Int        -> KBInt(x)
            is Long       -> KBLong(x)
            is Float      -> KBFloat(x)
            is Double     -> KBDouble(x)
            is Char       -> KBChar(x)
            is String     -> KBString(x)
            else          -> null
        }
    }
}

data object KBEmpty : KBValue<KBEmpty> {
    override val value = this
}

data object KBNone : KBValue<KBNone> {
    override val value = this
}

data class KBBool(override val value: Boolean) : KBValue<Boolean>

data class KBByte(override val value: Byte) : KBValue<Byte>

data class KBShort(override val value: Short) : KBValue<Short>

data class KBInt(override val value: Int) : KBValue<Int>

data class KBLong(override val value: Long) : KBValue<Long>

data class KBFloat(override val value: Float) : KBValue<Float>

data class KBDouble(override val value: Double) : KBValue<Double>

data class KBChar(override val value: Char) : KBValue<Char>

data class KBString(override val value: String) : KBValue<String>

data class KBArray(override val value: ArrayInstance) : KBValue<ArrayInstance>

data class KBData(override val value: DataInstance) : KBValue<DataInstance>

data class KBEnum(override val value: EnumInstance.Entry) : KBValue<EnumInstance.Entry>