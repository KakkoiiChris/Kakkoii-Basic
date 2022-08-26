package kakkoiichris.kb.script

import kakkoiichris.kb.lexer.Location
import kakkoiichris.kb.parser.Expr

sealed class Redirect(val origin: Location) : Throwable() {
    class Break(origin: Location, val label: String) : Redirect(origin)
    
    class Next(origin: Location, val pointer: Expr.Name) : Redirect(origin)
    
    class Return(origin: Location) : Redirect(origin)
    
    class Yield(origin: Location, val value: Any) : Redirect(origin)
}