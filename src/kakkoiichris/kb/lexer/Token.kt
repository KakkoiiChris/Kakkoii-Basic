package kakkoiichris.kb.lexer

data class Token(val context: Context, val type: Type, val value: Any = Unit) {
    enum class Type(val symbol: String) {
        // Keywords
        LET("let"),
        VAR("var"),
        EACH("each"),
        DO("do"),
        IF("if"),
        ELSE("else"),
        SWITCH("switch"),
        CASE("case"),
        WHILE("while"),
        UNTIL("until"),
        FOR("for"),
        TO("to"),
        STEP("step"),
        IN("in"),
        DATA("data"),
        SUB("sub"),
        WITH("with"),
        BREAK("break"),
        NEXT("next"),
        RETURN("return"),
        YIELD("yield"),
        LABEL("label"),
        TYPE("type"),
        ENUM("enum"),
        END("end"),
        
        // Types
        NONE("none"),
        BOOL("bool"),
        BYTE("byte"),
        SHORT("short"),
        INT("int"),
        LONG("long"),
        FLOAT("float"),
        DOUBLE("double"),
        CHAR("char"),
        STRING("string"),
        ANY("any"),
        
        // Assignment
        EQUAL_SIGN("="),
        PLUS_EQUAL("+="),
        DASH_EQUAL("-="),
        STAR_EQUAL("*="),
        SLASH_EQUAL("/="),
        PERCENT_EQUAL("%="),
        AMPERSAND_EQUAL("&="),
        
        // Disjunction
        OR("or"),
        
        // Conjunction
        AND("and"),
        
        // Equality
        DOUBLE_EQUAL("=="),
        LESS_GREATER("<>"),
        
        // Comparison
        LESS_SIGN("<"),
        LESS_EQUAL_SIGN("<="),
        GREATER_SIGN(">"),
        GREATER_EQUAL_SIGN(">="),
        
        // Type Check
        IS("is"),
        
        // Concatenate
        AMPERSAND("&"),
        
        // Additive
        PLUS("+"),
        DASH("-"),
        
        // Multiplicative
        STAR("*"),
        SLASH("/"),
        PERCENT("%"),
        
        // Type Cast
        AS("as"),
        
        // Prefix
        NOT("not"),
        POUND("#"),
        DOLLAR("$"),
        AT("@"),
        
        // Pipeline
        COLON(":"),
        
        // Postfix
        DOT("."),
        DOUBLE_COLON("::"),
        
        // Terminals
        VALUE("V"),
        WORD("N"),
        
        // Delimiters
        LEFT_PAREN("("),
        RIGHT_PAREN(")"),
        LEFT_SQUARE("["),
        RIGHT_SQUARE("]"),
        LEFT_BRACE("{"),
        RIGHT_BRACE("}"),
        COMMA(","),
        END_OF_FILE("0");
        
        override fun toString() = symbol
    }
}