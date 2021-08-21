package kakkoiichris.kb.lexer

data class Token(val loc: Location, val type: Type, val value: Any = Unit) {
    enum class Type(private val rep: kotlin.String) {
        // Keywords
        Let("let"),
        Var("var"),
        If("if"),
        Elif("elif"),
        Else("else"),
        While("while"),
        Do("do"),
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
        End("end"),
        
        // Types
        Void("void"),
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
        
        // Delimiters
        LeftParen("("),
        RightParen(")"),
        LeftSquare("["),
        RightSquare("]"),
        LeftBrace("{"),
        RightBrace("}"),
        Comma(","),
        EndOfFile("0");
        
        val kw get() = rep to this
        
        override fun toString() = rep
    }
}