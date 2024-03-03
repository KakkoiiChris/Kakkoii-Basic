package kakkoiichris.kb.lexer

/**
 * KakkoiiBasic
 *
 * Copyright (C) 2023, KakkoiiChris
 *
 * File:    Context.kt
 *
 * Created: Thursday, August 10, 2023, 22:06:34
 *
 * @author Christian Bryce Alexander
 */
data class Context(val location: Location, val region: IntRange, val line: String) {
    companion object {
        val none = Context(Location.none, -1..-1, "")
    }

    operator fun plus(that: Context) =
        Context(location, region.first..that.region.last, line)
}