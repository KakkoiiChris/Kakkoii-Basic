package kakkoiichris.kb.lexer

data class Token(val loc: Location, val type: Type, val value: Any = Unit) {
    enum class Type(private val rep: String) {
        // Keywords
        LET("let"),
        VAR("var"),
        IFF("if"),
        ELF("elif"),
        ELS("else"),
        WHL("while"),
        DOO("do"),
        FOR("for"),
        TOO("to"),
        STP("step"),
        INN("in"),
        DAT("data"),
        SBR("sub"),
        BRK("break"),
        NXT("next"),
        RET("return"),
        YLD("yield"),
        END("end"),
        
        // Types
        VOI("void"),
        BOL("bool"),
        BYT("byte"),
        SHR("short"),
        INT("int"),
        LNG("long"),
        FLT("float"),
        DBL("double"),
        CHR("char"),
        STR("string"),
        ANY("any"),
        
        // Assignment
        ASN("="),
        CAD("+="),
        CSB("-="),
        CML("*="),
        CDV("/="),
        CRM("%="),
        
        // Disjunction
        ORR("or"),
        
        // Conjunction
        AND("and"),
        
        // Equality
        EQU("=="),
        NEQ("<>"),
        
        // Comparison
        LSS("<"),
        LEQ("<="),
        GRT(">"),
        GEQ(">="),
        
        // Type Check
        ISS("is"),
        
        // Additive
        ADD("+"),
        SUB("-"),
        
        // Multiplicative
        MUL("*"),
        DIV("/"),
        REM("%"),
        
        // Type Cast
        AAS("as"),
        
        // Prefix
        NOT("not"),
        LEN("#"),
        
        // Pipeline
        PIP(":"),
        
        // Postfix
        DOT("."),
        
        // Terminals
        VAL("V"),
        NAM("N"),
        
        // Delimiters
        LPR("("),
        RPR(")"),
        LSQ("["),
        RSQ("]"),
        LBC("{"),
        RBC("}"),
        SEP(","),
        EOF("0");
        
        val kw get() = rep to this
        
        override fun toString() = rep
    }
}