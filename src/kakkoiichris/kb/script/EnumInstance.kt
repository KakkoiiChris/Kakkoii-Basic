package kakkoiichris.kb.script

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
    data class Entry(val name: String, val ordinal: Int, val value: Any)
}