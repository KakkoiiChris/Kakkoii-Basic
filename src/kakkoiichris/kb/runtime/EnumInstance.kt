package kakkoiichris.kb.runtime

import kakkoiichris.kb.parser.Expr

/**
 * KakkoiiBasic
 *
 * Copyright (C) 2023, KakkoiiChris
 *
 * File:    EnumInstance.kt
 *
 * Created: Tuesday, January 24, 2023, 11:00:08
 *
 * @author Christian Bryce Alexander
 */
class EnumInstance(val name: String, val entries: List<Entry>) {
    operator fun get(name: Expr.Name) =
        entries.first { it.name == name.value }
    
    operator fun get(index: Int) =
        entries[index]
    
    data class Entry(val type: String, val name: String, val ordinal: Int, val value: Any) {
        override fun toString()=
            "$type #$ordinal \$$name @$value"
    }
}