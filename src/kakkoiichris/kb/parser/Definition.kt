package kakkoiichris.kb.parser

/**
 * KakkoiiBasic
 *
 * Copyright (C) 2023, KakkoiiChris
 *
 * File:    Definition.kt
 *
 * Created: Monday, August 14, 2023, 21:55:11
 *
 * @author Christian Bryce Alexander
 */
data class Definition(val name: Expr.Name, val type: Expr.Type) {
    companion object {
        val none = Definition(Expr.Name.none, Expr.Type.none)
    }
}