package kakkoiichris.kb.lexer

data class Token(val location: Location, val type: Type, val value: Any = Unit) {
    enum class Type(val symbol: kotlin.String) {
        // Keywords
        Let("let"),
        Var("var"),
        Each("each"),
        Do("do"),
        If("if"),
        Elif("elif"),
        Else("else"),
        Switch("switch"),
        Case("case"),
        While("while"),
        Until("until"),
        For("for"),
        To("to"),
        Step("step"),
        In("in"),
        Data("data"),
        Sub("sub"),
        Break("break"),
        Next("next"),
        Return("return"),
        Yield("yield"),
        Goto("goto"),
        End("end"),
        
        // Types
        None("none"),
        Bool("bool"),
        Byte("byte"),
        Short("short"),
        Int("int"),
        Long("long"),
        Float("float"),
        Double("double"),
        Char("char"),
        String("string"),
        Any("any"),
        
        // Assignment
        EqualSign("="),
        PlusEqual("+="),
        MinusEqual("-="),
        StarEqual("*="),
        SlashEqual("/="),
        PercentEqual("%="),
        Dollar("$"),
        
        // Disjunction
        Or("or"),
        
        // Conjunction
        And("and"),
        
        // Equality
        DoubleEqual("=="),
        LessGreater("<>"),
        
        // Comparison
        LessSign("<"),
        LessEqualSign("<="),
        GreaterSign(">"),
        GreaterEqualSign(">="),
        
        // Type Check
        Is("is"),
        
        // Concatenate
        Ampersand("&"),
        
        // Additive
        Plus("+"),
        Minus("-"),
        
        // Multiplicative
        Star("*"),
        Slash("/"),
        Percent("%"),
        
        // Type Cast
        As("as"),
        
        // Prefix
        Not("not"),
        Pound("#"),
        
        // Pipeline
        Colon(":"),
        
        // Postfix
        Dot("."),
        
        // Terminals
        Value("V"),
        Word("N"),
        Label("@"),
        
        // Delimiters
        LeftParen("("),
        RightParen(")"),
        LeftSquare("["),
        RightSquare("]"),
        LeftBrace("{"),
        RightBrace("}"),
        Comma(","),
        EndOfFile("0");
        
        override fun toString() = symbol
    }
}