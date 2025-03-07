package kakkoiichris.kb.runtime

object Empty {
    override fun toString() = "empty"
}

fun Any.isEmptyValue() = when (this) {
    Empty            -> true

    is Boolean       -> !this

    is Byte          -> this == 0.toByte()

    is Short         -> this == 0.toShort()

    is Int           -> this == 0

    is Long          -> this == 0L

    is Float         -> this == 0F

    is Double        -> this == 0.0

    is Char          -> this == '\u0000'

    is String        -> isEmpty()

    is ArrayInstance -> isEmpty()

    is DataInstance  -> isEmpty()

    else             -> false
}