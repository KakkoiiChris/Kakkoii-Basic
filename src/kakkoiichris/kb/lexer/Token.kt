package kakkoiichris.kb.lexer

data class Token<X : Token.Type>(val context: Context, val type: X) {
    sealed interface Type {
        val symbol: String
    }

    enum class Keyword() : Type {
        // Keywords
        LET,
        VAR,
        EACH,
        DO,
        IF,
        ELSE,
        SWITCH,
        CASE,
        WHILE,
        UNTIL,
        FOR,
        TO,
        STEP,
        IN,
        DATA,
        SUB,
        WITH,
        BREAK,
        NEXT,
        RETURN,
        YIELD,
        LABEL,
        TYPE,
        ENUM,
        END,

        // Types
        NONE,
        BOOL,
        BYTE,
        SHORT,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        CHAR,
        STRING,
        ANY,

        // Disjunction
        OR,

        // Conjunction
        AND,

        // Type Check
        IS,

        // Type Cast
        AS,

        // Prefix
        NOT;

        override val symbol = name.lowercase()
    }

    enum class Symbol(override val symbol: String) : Type {
        // Assignment
        EQUAL_SIGN("="),
        PLUS_EQUAL("+="),
        DASH_EQUAL("-="),
        STAR_EQUAL("*="),
        SLASH_EQUAL("/="),
        PERCENT_EQUAL("%="),
        AMPERSAND_EQUAL("&="),

        // Equality
        DOUBLE_EQUAL("=="),
        LESS_GREATER("<>"),

        // Comparison
        LESS_SIGN("<"),
        LESS_EQUAL_SIGN("<="),
        GREATER_SIGN(">"),
        GREATER_EQUAL_SIGN(">="),

        // Concatenate
        AMPERSAND("&"),

        // Additive
        PLUS("+"),
        DASH("-"),

        // Multiplicative
        STAR("*"),
        SLASH("/"),
        PERCENT("%"),


        POUND("#"),
        DOLLAR("$"),
        AT("@"),

        // Pipeline
        COLON(":"),

        // Postfix
        DOT("."),
        DOUBLE_COLON("::"),

        // Delimiters
        LEFT_PAREN("("),
        RIGHT_PAREN(")"),
        LEFT_SQUARE("["),
        RIGHT_SQUARE("]"),
        LEFT_BRACE("{"),
        RIGHT_BRACE("}"),
        COMMA(","),
    }

    class Value(val value: Any) : Type {
        override val symbol = "<Value $value>"
    }

    class Word(val value: String) : Type {
        override val symbol = "<Name $value>"
    }

    data object EndOfFile : Type {
        override val symbol = "<EOF>"
    }
}