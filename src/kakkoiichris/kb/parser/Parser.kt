package kakkoiichris.kb.parser

import kakkoiichris.kb.lexer.Lexer
import kakkoiichris.kb.lexer.Location
import kakkoiichris.kb.lexer.Token
import kakkoiichris.kb.lexer.Token.Type.*
import kakkoiichris.kb.script.DataType
import kakkoiichris.kb.util.KBError

class Parser(private val lexer: Lexer) {
    private var currentToken = lexer.next()
    
    fun parse(): List<Stmt> {
        val stmts = mutableListOf<Stmt>()
        
        while (!isEndOfFile()) {
            stmts += stmt()
        }
        
        return stmts
    }
    
    private fun here() =
        currentToken.location
    
    private fun step() {
        if (lexer.hasNext()) {
            currentToken = lexer.next()
        }
    }
    
    private fun match(type: Token.Type) =
        currentToken.type == type
    
    private fun matchAny(vararg types: Token.Type): Boolean {
        for (type in types) {
            if (match(type)) {
                return true
            }
        }
        
        return false
    }
    
    private fun skip(type: Token.Type): Boolean {
        if (match(type)) {
            step()
            
            return true
        }
        
        return false
    }
    
    private fun mustSkip(type: Token.Type) {
        if (!skip(type)) {
            KBError.invalidTokenType(currentToken.type, type, here())
        }
    }
    
    private fun isEndOfFile() =
        match(END_OF_FILE)
    
    private fun stmt(): Stmt {
        val label = if (skip(LABEL)) name() else Expr.Name.none
        
        if (label.value.isNotEmpty()) {
            return when {
                match(DO)    -> doStmt(label)
                
                match(WHILE) -> whileStmt(label)
                
                match(UNTIL) -> untilStmt(label)
                
                match(FOR)   -> forStmt(label)
                
                else         -> KBError.nonLabeledStatement(currentToken.type, here())
            }
        }
        
        return when {
            matchAny(LET, VAR) -> declStmt()
            
            match(DO)          -> doStmt(label)
            
            match(IF)          -> ifStmt()
            
            match(SWITCH)      -> switchStmt()
            
            match(WHILE)       -> whileStmt(label)
            
            match(UNTIL)       -> untilStmt(label)
            
            match(FOR)         -> forStmt(label)
            
            match(DATA)        -> dataStmt()
            
            match(SUB)         -> subStmt()
            
            match(BREAK)       -> breakStmt()
            
            match(NEXT)        -> nextStmt()
            
            match(RETURN)      -> returnStmt()
            
            match(YIELD)       -> yieldStmt()
            
            match(TYPE)        -> typeStmt()
            
            match(ENUM)        -> enumStmt()
            
            else               -> expressionStmt()
        }
    }
    
    private fun declStmt(): Stmt {
        val location = here()
        
        val constant = skip(LET)
        
        if (!constant) {
            mustSkip(VAR)
        }
        
        val each = skip(EACH)
        
        val pairs = mutableListOf<Pair<Expr.Name, Expr.Type>>()
        
        if (each) {
            do {
                pairs += if (!match(COMMA)) {
                    val name = name()
                    
                    val type = if (skip(AS)) type() else Expr.Type.none
                    
                    name to type
                }
                else {
                    Expr.Name.none to Expr.Type.none
                }
            }
            while (skip(COMMA))
        }
        else {
            val name = name()
            
            val type = if (skip(AS)) type() else Expr.Type.none
            
            pairs += name to type
        }
        
        val expr = if (skip(EQUAL_SIGN)) expr() else Expr.Empty
        
        if (each) {
            return Stmt.DeclEach(location, constant, pairs, expr)
        }
        
        val (name, type) = pairs[0]
        
        return Stmt.Decl(location, constant, name, type, expr)
    }
    
    private fun block(vararg ends: Token.Type): Stmt.Block {
        val location = here()
        
        val stmts = mutableListOf<Stmt>()
        
        while (!matchAny(*ends)) {
            stmts += stmt()
        }
        
        return Stmt.Block(location, stmts)
    }
    
    private fun doStmt(label: Expr.Name): Stmt.Do {
        val location = here()
        
        mustSkip(DO)
        
        val body = block(END)
        
        mustSkip(END)
        mustSkip(DO)
        
        return Stmt.Do(location, label, body)
    }
    
    private fun ifStmt(): Stmt.If {
        val location = here()
        
        mustSkip(IF)
        
        val test = expr()
        
        val body = block(ELSE, END)
        
        val elze = if (skip(ELSE)) {
            if (match(IF)) {
                elseIfStmt()
            }
            else {
                block(END)
            }
        }
        else Stmt.None
        
        mustSkip(END)
        mustSkip(IF)
        
        return Stmt.If(location, test, body, elze)
    }
    
    private fun elseIfStmt(): Stmt.If {
        val location = here()
        
        mustSkip(IF)
        
        val test = expr()
        
        val body = block(ELSE, END)
        
        val elze = if (skip(ELSE)) {
            if (match(IF)) {
                elseIfStmt()
            }
            else {
                block(END)
            }
        }
        else Stmt.None
        
        return Stmt.If(location, test, body, elze)
    }
    
    private fun switchStmt(): Stmt.Switch {
        val location = here()
        
        mustSkip(SWITCH)
        
        val subject = expr()
        
        val cases = mutableListOf<Stmt.Switch.Case>()
        
        var elze: Stmt.Switch.Case.Else? = null
        
        while (!match(END)) {
            val caseLocation = here()
            
            mustSkip(CASE)
            
            when {
                skip(ELSE) -> if (elze == null) {
                    val block = block(END)
                    
                    mustSkip(END)
                    mustSkip(CASE)
                    
                    elze = Stmt.Switch.Case.Else(caseLocation, block)
                }
                else {
                    KBError.duplicateElseCase(here())
                }
                
                skip(IS)   -> {
                    val inverted = skip(NOT)
                    
                    val type = type()
                    
                    val block = block(END)
                    
                    mustSkip(END)
                    mustSkip(CASE)
                    
                    cases += Stmt.Switch.Case.Type(caseLocation, inverted, type, block)
                }
                
                else       -> {
                    val values = mutableListOf<Expr.Value>()
                    
                    do {
                        values += value()
                    }
                    while (skip(COMMA))
                    
                    val block = block(END)
                    
                    mustSkip(END)
                    mustSkip(CASE)
                    
                    cases += Stmt.Switch.Case.Values(caseLocation, values, block)
                }
            }
        }
        
        mustSkip(END)
        mustSkip(SWITCH)
        
        if (elze != null) {
            cases += elze
        }
        
        return Stmt.Switch(location, subject, cases)
    }
    
    private fun whileStmt(label: Expr.Name): Stmt.While {
        val location = here()
        
        mustSkip(WHILE)
        
        val test = expr()
        
        val body = block(END)
        
        mustSkip(END)
        mustSkip(WHILE)
        
        return Stmt.While(location, label, test, body)
    }
    
    private fun untilStmt(label: Expr.Name): Stmt.Until {
        val location = here()
        
        mustSkip(UNTIL)
        
        val test = expr()
        
        val body = block(END)
        
        mustSkip(END)
        mustSkip(UNTIL)
        
        return Stmt.Until(location, label, test, body)
    }
    
    private fun forStmt(label: Expr.Name): Stmt {
        val location = here()
        
        mustSkip(FOR)
        
        val each = skip(EACH)
        
        val pairs = mutableListOf<Pair<Expr.Name, Expr.Type>>()
        
        if (each) {
            do {
                pairs += if (!match(COMMA)) {
                    val name = name()
                    
                    val type = if (skip(AS)) type() else Expr.Type.none
                    
                    name to type
                }
                else {
                    Expr.Name.none to Expr.Type.none
                }
            }
            while (skip(COMMA))
        }
        else {
            val name = name()
            
            val type = if (skip(AS)) type() else Expr.Type.none
            
            pairs += name to type
        }
        
        return when {
            each                     -> {
                mustSkip(IN)
                
                val decl = Stmt.DeclEach(pairs[0].first.location, false, pairs, Expr.Empty)
                
                val iterable = expr()
                
                val body = block(END)
                
                mustSkip(END)
                mustSkip(FOR)
                
                Stmt.ForIterateEach(location, label, decl, iterable, body)
            }
            
            skip(IN)                 -> {
                val (name, type) = pairs[0]
                
                val decl = Stmt.Decl(name.location, false, name, type, Expr.Empty)
                
                val iterable = expr()
                
                val body = block(END)
                
                mustSkip(END)
                mustSkip(FOR)
                
                Stmt.ForIterate(location, label, decl, iterable, body)
            }
            
            matchAny(EQUAL_SIGN, TO) -> {
                val start = if (skip(EQUAL_SIGN)) expr() else Expr.Empty
                
                val (name, type) = pairs[0]
                
                val decl = Stmt.Decl(name.location, false, name, type, start)
                
                mustSkip(TO)
                
                val to = expr()
                
                val step = if (skip(STEP)) expr() else 1.toExpr()
                
                val body = block(END)
                
                mustSkip(END)
                mustSkip(FOR)
                
                Stmt.ForCounter(location, label, decl, to, step, body)
            }
            
            else                     -> KBError.invalidForLoop(here())
        }
    }
    
    private fun dataStmt(): Stmt.Data {
        val location = here()
        
        mustSkip(DATA)
        
        val name = name()
        
        val decls = mutableListOf<Stmt.Decl>()
        
        if (!skip(END)) {
            do {
                val declLoc = here()
                
                val constant = skip(LET)
                
                if (!constant) {
                    mustSkip(VAR)
                }
                
                val declName = name()
                
                val type = if (skip(AS)) type() else Expr.Type(declName.location, DataType.Inferred)
                
                val expr = if (skip(EQUAL_SIGN)) expr() else Expr.Empty
                
                decls += Stmt.Decl(declLoc, constant, declName, type, expr)
            }
            while (skip(COMMA))
            
            mustSkip(END)
            mustSkip(DATA)
        }
        
        return Stmt.Data(location, name, decls)
    }
    
    private fun subStmt(): Stmt.Sub {
        val location = here()
        
        mustSkip(SUB)
        
        val name = name()
        
        val type = if (skip(AS)) type() else DataType.Primitive.NONE.toType()
        
        val params = mutableListOf<Stmt.Decl>()
        
        if (skip(WITH)) {
            do {
                val paramName = name()
                
                mustSkip(AS)
                
                val paramType = type()
                
                val expr = if (skip(EQUAL_SIGN)) expr() else Expr.Empty
                
                params += Stmt.Decl(name.location, false, paramName, paramType, expr)
            }
            while (skip(COMMA))
        }
        
        val body = block(END)
        
        mustSkip(END)
        mustSkip(SUB)
        
        return Stmt.Sub(location, name, type, params, body)
    }
    
    private fun breakStmt(): Stmt.Break {
        val location = here()
        
        mustSkip(BREAK)
        
        val destination = if (skip(TO)) name() else Expr.Name.none
        
        return Stmt.Break(location, destination)
    }
    
    private fun nextStmt(): Stmt.Next {
        val location = here()
        
        mustSkip(NEXT)
        
        val destination = if (skip(TO)) name() else Expr.Name.none
        
        return Stmt.Next(location, destination)
    }
    
    private fun returnStmt(): Stmt.Return {
        val location = here()
        
        mustSkip(RETURN)
        
        return Stmt.Return(location)
    }
    
    private fun yieldStmt(): Stmt.Yield {
        val location = here()
        
        mustSkip(YIELD)
        
        val expr = expr()
        
        return Stmt.Yield(location, expr)
    }
    
    private fun typeStmt(): Stmt.Type {
        val location = here()
        
        mustSkip(TYPE)
        
        val full = type()
        
        mustSkip(AS)
        
        val alias = name()
        
        return Stmt.Type(location, full, alias)
    }
    
    private fun enumStmt(): Stmt {
        val location = here()
        
        mustSkip(ENUM)
        
        val name = name()
        
        return if (match(AS)) {
            dataEnum(location, name)
        }
        else {
            basicEnum(location, name)
        }
    }
    
    private fun basicEnum(location: Location, name: Expr.Name): Stmt.BasicEnum {
        val entries = mutableListOf<Stmt.BasicEnum.Entry>()
        
        var ordinal = 0
        
        do {
            val entryName = name()
            
            val value = if (skip(EQUAL_SIGN)) value() else ordinal.toExpr()
            
            entries += Stmt.BasicEnum.Entry(entryName.location, entryName, ordinal.toExpr(), value)
            
            ordinal++
        }
        while (skip(COMMA))
        
        mustSkip(END)
        mustSkip(ENUM)
        
        return Stmt.BasicEnum(location, name, entries)
    }
    
    private fun dataEnum(location: Location, name: Expr.Name): Stmt.DataEnum {
        val type = if (skip(AS)) type() else Expr.Type(name.location, DataType.Primitive.INT)
        
        val entries = mutableListOf<Stmt.DataEnum.Entry>()
        
        var ordinal = 0
        
        do {
            val entryName = name()
            
            val value = if (skip(EQUAL_SIGN)) instantiate() else TODO("DATA ENUM ENTRY MUST HAVE INSTANTIATE")
            
            entries += Stmt.DataEnum.Entry(entryName.location, entryName, ordinal.toExpr(), value)
            
            ordinal++
        }
        while (skip(COMMA))
        
        mustSkip(END)
        mustSkip(ENUM)
        
        return Stmt.DataEnum(location, name, type, entries)
    }
    
    private fun expressionStmt() =
        Stmt.Expression(expr())
    
    private fun expr() = assign()
    
    private fun assign(): Expr {
        val expr = disjunction()
        
        return if (matchAny(EQUAL_SIGN, PLUS_EQUAL, DASH_EQUAL, STAR_EQUAL, SLASH_EQUAL, PERCENT_EQUAL, AMPERSAND_EQUAL)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            fun desugar(newOp: Expr.Binary.Operator) =
                Expr.Binary(
                    op.location,
                    Expr.Binary.Operator.ASSIGN,
                    expr,
                    Expr.Binary(op.location, newOp, expr, disjunction())
                )
            
            when (op.type) {
                PLUS_EQUAL      -> desugar(Expr.Binary.Operator.ADD)
                
                DASH_EQUAL      -> desugar(Expr.Binary.Operator.SUBTRACT)
                
                STAR_EQUAL      -> desugar(Expr.Binary.Operator.MULTIPLY)
                
                SLASH_EQUAL     -> desugar(Expr.Binary.Operator.DIVIDE)
                
                PERCENT_EQUAL   -> desugar(Expr.Binary.Operator.MODULUS)
                
                AMPERSAND_EQUAL -> desugar(Expr.Binary.Operator.CONCAT)
                
                else            -> Expr.Binary(op.location, Expr.Binary.Operator[op.type], expr, disjunction())
            }
        }
        else {
            expr
        }
    }
    
    private fun disjunction(): Expr {
        var expr = conjunction()
        
        while (match(OR)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.location, Expr.Binary.Operator[op.type], expr, conjunction())
        }
        
        return expr
    }
    
    private fun conjunction(): Expr {
        var expr = equality()
        
        while (match(AND)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.location, Expr.Binary.Operator[op.type], expr, equality())
        }
        
        return expr
    }
    
    private fun equality(): Expr {
        var expr = comparison()
        
        while (matchAny(DOUBLE_EQUAL, LESS_GREATER)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.location, Expr.Binary.Operator[op.type], expr, comparison())
        }
        
        return expr
    }
    
    private fun comparison(): Expr {
        var expr = typeCheck()
        
        while (matchAny(LESS_SIGN, LESS_EQUAL_SIGN, GREATER_SIGN, GREATER_EQUAL_SIGN)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.location, Expr.Binary.Operator[op.type], expr, typeCheck())
        }
        
        return expr
    }
    
    private fun typeCheck(): Expr {
        var expr = concat()
        
        while (match(IS)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            val opType = if (skip(NOT)) NOT else IS
            
            expr = Expr.Binary(op.location, Expr.Binary.Operator[opType], expr, type())
        }
        
        return expr
    }
    
    private fun concat(): Expr {
        var expr = additive()
        
        while (match(AMPERSAND)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.location, Expr.Binary.Operator[op.type], expr, additive())
        }
        
        return expr
    }
    
    private fun additive(): Expr {
        var expr = multiplicative()
        
        while (matchAny(PLUS, DASH)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.location, Expr.Binary.Operator[op.type], expr, multiplicative())
        }
        
        return expr
    }
    
    private fun multiplicative(): Expr {
        var expr = typeCast()
        
        while (matchAny(STAR, SLASH, PERCENT)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.location, Expr.Binary.Operator[op.type], expr, typeCast())
        }
        
        return expr
    }
    
    private fun typeCast(): Expr {
        var expr = prefix()
        
        while (match(AS)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.location, Expr.Binary.Operator[op.type], expr, type())
        }
        
        return expr
    }
    
    private fun prefix(): Expr {
        return if (matchAny(DASH, NOT, POUND, DOLLAR, AT)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            Expr.Unary(op.location, Expr.Unary.Operator[op.type], prefix())
        }
        else {
            pipeline()
        }
    }
    
    private fun pipeline(): Expr {
        var expr = postfix()
        
        while (matchAny(COLON)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            val right = postfix()
            
            expr = when (right) {
                is Expr.Invoke -> {
                    val args = mutableListOf(expr)
                    
                    args.addAll(right.args)
                    
                    Expr.Invoke(op.location, right.name, args)
                }
                
                is Expr.Name   -> Expr.Invoke(op.location, right, listOf(expr))
                
                else           -> KBError.invalidPipelineOperand(right.location)
            }
        }
        
        return expr
    }
    
    private fun postfix(): Expr {
        var expr = terminal()
        
        while (matchAny(DOT, LEFT_SQUARE, LEFT_PAREN, LEFT_BRACE, DOUBLE_COLON)) {
            expr = when {
                match(DOT)          -> member(expr)
                
                match(LEFT_SQUARE)  -> index(expr)
                
                match(LEFT_PAREN)   -> invoke(expr)
                
                match(LEFT_BRACE)   -> instantiate(expr)
                
                match(DOUBLE_COLON) -> entry(expr)
                
                else                -> KBError.failure("Broken postfix operator '${currentToken.type}'!", currentToken.location)
            }
        }
        
        return expr
    }
    
    private fun member(expr: Expr):Expr.GetMember {
        val op = currentToken
        
        mustSkip(DOT)
        
        return Expr.GetMember(op.location, expr, name())
    }
    
    private fun index(expr: Expr): Expr {
        mustSkip(LEFT_SQUARE)
        
        val indices = mutableListOf<Expr>()
        
        do {
            indices += expr()
        }
        while (skip(COMMA))
        
        mustSkip(RIGHT_SQUARE)
        
        var subExpr = expr
        
        for (index in indices) {
            subExpr = Expr.GetIndex(index.location, subExpr, index)
        }
        
        if (matchAny(EQUAL_SIGN, PLUS_EQUAL, DASH_EQUAL, STAR_EQUAL, SLASH_EQUAL, PERCENT_EQUAL)) {
            val subOp = currentToken
            
            mustSkip(subOp.type)
            
            val top = subExpr as Expr.GetIndex
            
            val location = top.location
            val target = top.target
            val index = top.index
            
            fun desugar(newOp: Expr.Binary.Operator): Expr.SetIndex {
                val getIndex = Expr.GetIndex(location, target, index)
                
                val binary = Expr.Binary(subOp.location, newOp, getIndex, expr())
                
                return Expr.SetIndex(location, target, index, binary)
            }
            
            subExpr = when (subOp.type) {
                PLUS_EQUAL    -> desugar(Expr.Binary.Operator.ADD)
                
                DASH_EQUAL    -> desugar(Expr.Binary.Operator.SUBTRACT)
                
                STAR_EQUAL    -> desugar(Expr.Binary.Operator.MULTIPLY)
                
                SLASH_EQUAL   -> desugar(Expr.Binary.Operator.DIVIDE)
                
                PERCENT_EQUAL -> desugar(Expr.Binary.Operator.MODULUS)
                
                else          -> Expr.SetIndex(location, target, index, expr())
            }
        }
        
        return subExpr
    }
    
    private fun invoke(expr: Expr): Expr.Invoke {
        val op = currentToken
        
        mustSkip(LEFT_PAREN)
        
        val name = expr as? Expr.Name ?: KBError.invalidInvocationTarget(expr.location)
        
        val args = mutableListOf<Expr>()
        
        if (!skip(RIGHT_PAREN)) {
            do {
                args += if (match(COMMA)) Expr.Empty else expr()
            }
            while (skip(COMMA))
            
            mustSkip(RIGHT_PAREN)
        }
        
        return Expr.Invoke(op.location, name, args)
    }
    
    private fun instantiate(expr: Expr = name()): Expr.Instantiate {
        val op = currentToken
        
        mustSkip(LEFT_BRACE)
        
        val elements = mutableListOf<Expr>()
        
        if (!skip(RIGHT_BRACE)) {
            do {
                elements += if (match(COMMA)) Expr.Empty else expr()
            }
            while (skip(COMMA))
            
            mustSkip(RIGHT_BRACE)
        }
        
        return Expr.Instantiate(op.location, expr as? Expr.Name ?: KBError.invalidInstantiationTarget(expr.location), elements)
    }
    
    private fun entry(expr: Expr): Expr.GetEntry {
        val op = currentToken
        
        mustSkip(DOUBLE_COLON)
        
        if (expr !is Expr.Name) {
            TODO("GET ENTRY ENUM NAME")
        }
        
        return Expr.GetEntry(op.location, expr, name())
    }
    
    private fun terminal(): Expr {
        return when {
            match(VALUE)       -> value()
            
            match(WORD)        -> name()
            
            match(LEFT_SQUARE) -> array()
            
            match(LEFT_BRACE)  -> data()
            
            match(LEFT_PAREN)  -> nested()
            
            else               -> KBError.invalidTerminal(currentToken.type, here())
        }
    }
    
    private fun value(): Expr.Value {
        val token = currentToken
        
        mustSkip(VALUE)
        
        return Expr.Value(token.location, token.value)
    }
    
    private fun name(): Expr.Name {
        val token = currentToken
        
        mustSkip(WORD)
        
        return Expr.Name(token.location, token.value as String)
    }
    
    private fun type(): Expr.Type {
        val location = here()
        
        var type = if (match(WORD)) {
            DataType.Data(name())
        }
        else {
            val token = currentToken
            
            val primitive = DataType
                .Primitive
                .values()
                .firstOrNull {
                    token
                        .type
                        .toString()
                        .equals(it.name, true)
                }
                ?: KBError.invalidDataType(token.type, token.location)
            
            mustSkip(token.type)
            
            primitive
        }
        
        while (skip(LEFT_SQUARE)) {
            when {
                skip(RIGHT_SQUARE) -> type = DataType.Array(type)
                
                else               -> {
                    do {
                        val initSize = if (!matchAny(COMMA, RIGHT_SQUARE)) expr() else null
                        
                        type = DataType.Array(type, initSize)
                    }
                    while (skip(COMMA))
                    
                    mustSkip(RIGHT_SQUARE)
                }
            }
        }
        
        if (type is DataType.Array) {
            val initSizes = mutableListOf<Expr?>()
            
            do {
                if (type is DataType.Array) {
                    initSizes += type.initSize
                    
                    type = type.iterableType
                }
                else break
            }
            while (true)
            
            while (initSizes.isNotEmpty()) {
                type = DataType.Array(type, initSizes.removeAt(0))
            }
        }
        
        if (skip(STAR)) {
            type = DataType.Vararg(type)
        }
        
        return Expr.Type(location, type)
    }
    
    private fun array(): Expr.Array {
        val location = here()
        
        mustSkip(LEFT_SQUARE)
        
        val elements = mutableListOf<Expr>()
        
        if (!skip(RIGHT_SQUARE)) {
            do {
                elements += expr()
            }
            while (skip(COMMA))
            
            mustSkip(RIGHT_SQUARE)
        }
        
        return Expr.Array(location, elements)
    }
    
    private fun data(): Expr.Instantiate {
        val location = here()
        
        mustSkip(LEFT_BRACE)
        
        val elements = mutableListOf<Expr>()
        
        if (!skip(RIGHT_BRACE)) {
            do {
                if (!match(COMMA)) {
                    elements += expr()
                }
            }
            while (skip(COMMA))
            
            mustSkip(RIGHT_BRACE)
        }
        
        return Expr.Instantiate(location, Expr.Name.none, elements)
    }
    
    private fun nested(): Expr {
        mustSkip(LEFT_PAREN)
        
        val expr = expr()
        
        mustSkip(RIGHT_PAREN)
        
        return expr
    }
}