package kakkoiichris.kb.script

import kakkoiichris.kb.lexer.Context
import kakkoiichris.kb.lexer.Location
import kakkoiichris.kb.parser.Expr

sealed class Redirect(val origin: Context) : Throwable() {
    class Break(origin: Context, val label: Expr.Name) : Redirect(origin)
    
    class Next(origin: Context, val label:Expr.Name) : Redirect(origin)
    
    class Return(origin: Context) : Redirect(origin)
    
    class Yield(origin: Context, val value: Any) : Redirect(origin)
}