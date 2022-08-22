package kakkoiichris.kb.parser

import kakkoiichris.kb.lexer.Lexer
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
        match(EndOfFile)
    
    private fun stmt(): Stmt {
        val label = if (match(Label)) {
            val token = currentToken
            
            mustSkip(Label)
            
            token.value as String
        }
        else ""
        
        return when {
            matchAny(Let, Var) -> declStmt(label)
            
            match(Do)          -> doStmt(label)
            
            match(If)          -> ifStmt(label)
            
            match(Switch)      -> switchStmt(label)
            
            match(While)       -> whileStmt(label)
            
            match(For)         -> forStmt(label)
            
            match(Data)        -> dataStmt(label)
            
            match(Sub)         -> subStmt(label)
            
            match(Break)       -> breakStmt(label)
            
            match(Next)        -> nextStmt(label)
            
            match(Return)      -> returnStmt(label)
            
            match(Yield)       -> yieldStmt(label)
            
            match(Goto)        -> gotoStmt(label)
            
            else               -> expressionStmt(label)
        }
    }
    
    private fun declStmt(label: String): Stmt {
        val location = here()
        
        val constant = skip(Let)
        
        if (!constant) {
            mustSkip(Var)
        }
        
        val each = skip(Each)
        
        val pairs = mutableListOf<Pair<Expr.Name, Expr.Type>>()
        
        if (each) {
            do {
                pairs += if (!match(Comma)) {
                    val name = name()
                    
                    val type = if (skip(As)) type() else Expr.Type.none
                    
                    name to type
                }
                else {
                    Expr.Name.none to Expr.Type.none
                }
            }
            while (skip(Comma))
        }
        else {
            val name = name()
            
            val type = if (skip(As)) type() else Expr.Type.none
            
            pairs += name to type
        }
        
        val expr = if (skip(EqualSign)) expr() else Expr.Empty
        
        if (each) {
            return Stmt.DeclEach(location, label, constant, pairs, expr)
        }
        
        val (name, type) = pairs[0]
        
        return Stmt.Decl(location, label, constant, name, type, expr)
    }
    
    private fun block(vararg ends: Token.Type): Stmt.Block {
        val location = here()
        
        val stmts = mutableListOf<Stmt>()
        
        while (!matchAny(*ends)) {
            stmts += stmt()
        }
        
        return Stmt.Block(location, stmts)
    }
    
    private fun doStmt(label: String): Stmt.Do {
        val location = here()
        
        mustSkip(Do)
        
        val body = block(End)
        
        mustSkip(End)
        mustSkip(Do)
        
        return Stmt.Do(location, label, body)
    }
    
    private fun ifStmt(label: String): Stmt.If {
        val location = here()
        
        mustSkip(If)
        
        val branches = mutableListOf<Pair<Expr, Stmt.Block>>()
        
        do {
            val test = expr()
            
            val body = block(Elif, Else, End)
            
            branches += test to body
        }
        while (skip(Elif))
        
        val elze = if (skip(Else)) block(End) else Stmt.None
        
        mustSkip(End)
        mustSkip(If)
        
        return Stmt.If(location, label, branches, elze)
    }
    
    private fun switchStmt(label: String): Stmt.Switch {
        val location = here()
        
        mustSkip(Switch)
        
        val subject = expr()
        
        val cases = mutableListOf<Stmt.Switch.Case>()
        
        var elze: Stmt.Switch.Case.Else? = null
        
        while (!skip(End)) {
            val caseLoc = here()
            
            mustSkip(Case)
            
            when {
                skip(Else) -> if (elze == null) {
                    val block = block(End)
                    
                    mustSkip(End)
                    
                    elze = Stmt.Switch.Case.Else(caseLoc, block)
                }
                else {
                    KBError.duplicateElseCase(here())
                }
                
                skip(Is)   -> {
                    val type = type()
                    
                    val block = block(End)
                    
                    mustSkip(End)
                    mustSkip(Case)
                    
                    cases += Stmt.Switch.Case.Type(caseLoc, type, block)
                }
                
                else       -> {
                    val values = mutableListOf<Expr.Value>()
                    
                    do {
                        values += value()
                    }
                    while (skip(Comma))
                    
                    val block = block(End)
                    
                    mustSkip(End)
                    mustSkip(Case)
                    
                    cases += Stmt.Switch.Case.Values(caseLoc, values, block)
                }
            }
        }
        
        mustSkip(Switch)
        
        if (elze != null) {
            cases += elze
        }
        
        return Stmt.Switch(location, label, subject, cases)
    }
    
    private fun whileStmt(label: String): Stmt.While {
        val location = here()
        
        mustSkip(While)
        
        val test = expr()
        
        val body = block(End)
        
        mustSkip(End)
        mustSkip(While)
        
        return Stmt.While(location, label, test, body)
    }
    
    private fun forStmt(label: String): Stmt {
        val location = here()
        
        mustSkip(For)
        
        val each = skip(Each)
        
        val pairs = mutableListOf<Pair<Expr.Name, Expr.Type>>()
        
        if (each) {
            do {
                pairs += if (!match(Comma)) {
                    val name = name()
                    
                    val type = if (skip(As)) type() else Expr.Type.none
                    
                    name to type
                }
                else {
                    Expr.Name.none to Expr.Type.none
                }
            }
            while (skip(Comma))
        }
        else {
            val name = name()
            
            val type = if (skip(As)) type() else Expr.Type.none
            
            pairs += name to type
        }
        
        return when {
            each                    -> {
                mustSkip(In)
                
                val decl = Stmt.DeclEach(pairs[0].first.location, "", false, pairs, Expr.Empty)
                
                val iterable = expr()
                
                val body = block(End)
                
                mustSkip(End)
                mustSkip(For)
                
                Stmt.ForIterateEach(location, label, decl, iterable, body)
            }
            
            skip(In)                -> {
                val (name, type) = pairs[0]
                
                val decl = Stmt.Decl(name.location, "", false, name, type, Expr.Empty)
                
                val iterable = expr()
                
                val body = block(End)
                
                mustSkip(End)
                mustSkip(For)
                
                Stmt.ForIterate(location, label, decl, iterable, body)
            }
            
            matchAny(EqualSign, To) -> {
                val start = if (skip(EqualSign)) expr() else Expr.Empty
                
                val (name, type) = pairs[0]
                
                val decl = Stmt.Decl(name.location, "", false, name, type, start)
                
                mustSkip(To)
                
                val to = expr()
                
                val step = if (skip(Step)) expr() else 1.toExpr()
                
                val body = block(End)
                
                mustSkip(End)
                mustSkip(For)
                
                Stmt.ForCounter(location, label, decl, to, step, body)
            }
            
            else                    -> KBError.invalidForLoop(here())
        }
    }
    
    private fun dataStmt(label: String): Stmt.Data {
        val location = here()
        
        mustSkip(Data)
        
        val name = name()
        
        val decls = mutableListOf<Stmt.Decl>()
        
        if (!skip(End)) {
            do {
                val declLoc = here()
                
                val constant = skip(Let)
                
                if (!constant) {
                    mustSkip(Var)
                }
                
                val declName = name()
                
                val type = if (skip(As)) type() else Expr.Type(declName.location, DataType.Inferred)
                
                val expr = if (skip(EqualSign)) expr() else Expr.Empty
                
                decls += Stmt.Decl(declLoc, label, constant, declName, type, expr)
            }
            while (skip(Comma))
            
            mustSkip(End)
            mustSkip(Data)
        }
        
        return Stmt.Data(location, label, name, decls)
    }
    
    private fun subStmt(label: String): Stmt.Sub {
        val location = here()
        
        mustSkip(Sub)
        
        val name = name()
        
        val params = mutableListOf<Stmt.Decl>()
        
        if (skip(LeftParen) && !skip(RightParen)) {
            do {
                val paramName = name()
                
                mustSkip(As)
                
                val paramType = type()
                
                params += Stmt.Decl(name.location, "", false, paramName, paramType, Expr.Empty)
            }
            while (skip(Comma))
            
            mustSkip(RightParen)
        }
        
        val type = if (skip(As)) type() else DataType.Primitive.NONE.toType()
        
        val body = block(End)
        
        mustSkip(End)
        mustSkip(Sub)
        
        return Stmt.Sub(location, label, name, params, type, body)
    }
    
    private fun breakStmt(label: String): Stmt.Break {
        val location = here()
        
        mustSkip(Break)
        
        val destination = if (match(Label)) {
            val token = currentToken.value as String
            
            mustSkip(Label)
            
            token
        }
        else ""
        
        return Stmt.Break(location, label, destination)
    }
    
    private fun nextStmt(label: String): Stmt.Next {
        val location = here()
        
        mustSkip(Next)
        
        val pointer = name()
        
        return Stmt.Next(location, label, pointer)
    }
    
    private fun returnStmt(label: String): Stmt.Return {
        val location = here()
        
        mustSkip(Return)
        
        return Stmt.Return(location, label)
    }
    
    private fun yieldStmt(label: String): Stmt.Yield {
        val location = here()
        
        mustSkip(Yield)
        
        val expr = expr()
        
        return Stmt.Yield(location, label, expr)
    }
    
    private fun gotoStmt(label: String): Stmt.Goto {
        val location = here()
        
        mustSkip(Goto)
        
        val destination = currentToken
        
        mustSkip(Label)
        
        return Stmt.Goto(location, label, destination.value as String)
    }
    
    private fun expressionStmt(label: String) =
        Stmt.Expression(label, expr())
    
    private fun expr() = assign()
    
    private fun assign(): Expr {
        val expr = disjunction()
        
        return if (matchAny(EqualSign, PlusEqual, MinusEqual, StarEqual, SlashEqual, PercentEqual, Dollar)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            fun desugar(newOp: Expr.Binary.Operator) =
                Expr.Binary(
                    op.location,
                    Expr.Binary.Operator.Assign,
                    expr,
                    Expr.Binary(op.location, newOp, expr, disjunction())
                )
            
            when (op.type) {
                PlusEqual    -> desugar(Expr.Binary.Operator.Add)
                
                MinusEqual   -> desugar(Expr.Binary.Operator.Subtract)
                
                StarEqual    -> desugar(Expr.Binary.Operator.Multiply)
                
                SlashEqual   -> desugar(Expr.Binary.Operator.Divide)
                
                PercentEqual -> desugar(Expr.Binary.Operator.Modulus)
                
                else         -> Expr.Binary(op.location, Expr.Binary.Operator[op.type], expr, disjunction())
            }
        }
        else {
            expr
        }
    }
    
    private fun disjunction(): Expr {
        var expr = conjunction()
        
        while (match(Or)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.location, Expr.Binary.Operator[op.type], expr, conjunction())
        }
        
        return expr
    }
    
    private fun conjunction(): Expr {
        var expr = equality()
        
        while (match(And)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.location, Expr.Binary.Operator[op.type], expr, equality())
        }
        
        return expr
    }
    
    private fun equality(): Expr {
        var expr = comparison()
        
        while (matchAny(DoubleEqual, LessGreater)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.location, Expr.Binary.Operator[op.type], expr, comparison())
        }
        
        return expr
    }
    
    private fun comparison(): Expr {
        var expr = typeCheck()
        
        while (matchAny(LessSign, LessEqualSign, GreaterSign, GreaterEqualSign)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.location, Expr.Binary.Operator[op.type], expr, typeCheck())
        }
        
        return expr
    }
    
    private fun typeCheck(): Expr {
        var expr = concat()
        
        while (match(Is)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.location, Expr.Binary.Operator[op.type], expr, type())
        }
        
        return expr
    }
    
    private fun concat(): Expr {
        var expr = additive()
        
        while (match(Ampersand)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.location, Expr.Binary.Operator[op.type], expr, additive())
        }
        
        return expr
    }
    
    private fun additive(): Expr {
        var expr = multiplicative()
        
        while (matchAny(Plus, Minus)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.location, Expr.Binary.Operator[op.type], expr, multiplicative())
        }
        
        return expr
    }
    
    private fun multiplicative(): Expr {
        var expr = typeCast()
        
        while (matchAny(Star, Slash, Percent)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.location, Expr.Binary.Operator[op.type], expr, typeCast())
        }
        
        return expr
    }
    
    private fun typeCast(): Expr {
        var expr = prefix()
        
        while (match(As)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.location, Expr.Binary.Operator[op.type], expr, type())
        }
        
        return expr
    }
    
    private fun prefix(): Expr {
        return if (matchAny(Minus, Not, Pound)) {
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
        
        while (matchAny(Colon)) {
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
        
        while (matchAny(Dot, LeftSquare, LeftParen, LeftBrace)) {
            val op = currentToken
            
            mustSkip(op.type)
            
            expr = when (op.type) {
                Dot        -> Expr.GetMember(op.location, expr, name())
                
                LeftSquare -> {
                    val indices = mutableListOf<Expr>()
                    
                    do {
                        indices += expr()
                    }
                    while (skip(Comma))
                    
                    mustSkip(RightSquare)
                    
                    var subExpr = expr
                    
                    for (index in indices) {
                        subExpr = Expr.GetIndex(index.location, subExpr, index)
                    }
                    
                    if (matchAny(EqualSign, PlusEqual, MinusEqual, StarEqual, SlashEqual, PercentEqual)) {
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
                            PlusEqual    -> desugar(Expr.Binary.Operator.Add)
                            
                            MinusEqual   -> desugar(Expr.Binary.Operator.Subtract)
                            
                            StarEqual    -> desugar(Expr.Binary.Operator.Multiply)
                            
                            SlashEqual   -> desugar(Expr.Binary.Operator.Divide)
                            
                            PercentEqual -> desugar(Expr.Binary.Operator.Modulus)
                            
                            else         -> Expr.SetIndex(location, target, index, expr())
                        }
                    }
                    
                    subExpr
                }
                
                LeftParen  -> {
                    val name = expr as? Expr.Name ?: KBError.invalidInvocationTarget(expr.location)
                    
                    val args = mutableListOf<Expr>()
                    
                    if (!skip(RightParen)) {
                        do {
                            args += if (match(Comma)) Expr.Empty else expr()
                        }
                        while (skip(Comma))
                        
                        mustSkip(RightParen)
                    }
                    
                    Expr.Invoke(op.location, name, args)
                }
                
                LeftBrace  -> {
                    val elements = mutableListOf<Expr>()
                    
                    if (!skip(RightBrace)) {
                        do {
                            elements += if (match(Comma)) Expr.Empty else expr()
                        }
                        while (skip(Comma))
                        
                        mustSkip(RightBrace)
                    }
                    
                    Expr.Instantiate(op.location, expr as? Expr.Name ?: KBError.invalidInstantiationTarget(expr.location), elements)
                }
                
                else       -> KBError.failure("Broken postfix operator '${op.type}'!", op.location)
            }
        }
        
        return expr
    }
    
    private fun terminal(): Expr {
        return when {
            match(Value)      -> value()
            
            match(Word)       -> name()
            
            match(LeftSquare) -> array()
            
            match(LeftBrace)  -> data()
            
            match(LeftParen)  -> nested()
            
            else              -> KBError.invalidTerminal(currentToken.type, here())
        }
    }
    
    private fun value(): Expr.Value {
        val token = currentToken
        
        mustSkip(Value)
        
        return Expr.Value(token.location, token.value)
    }
    
    private fun name(): Expr.Name {
        val token = currentToken
        
        mustSkip(Word)
        
        return Expr.Name(token.location, token.value as String)
    }
    
    private fun type(): Expr.Type {
        val location = here()
        
        var type = if (match(Word)) {
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
        
        while (skip(LeftSquare)) {
            when {
                skip(RightSquare) -> type = DataType.Array(type)
                
                else              -> {
                    do {
                        val initSize = if (!matchAny(Comma, RightSquare)) expr() else null
                        
                        type = DataType.Array(type, initSize)
                    }
                    while (skip(Comma))
                    
                    mustSkip(RightSquare)
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
        
        if (skip(Star)) {
            type = DataType.Vararg(type)
        }
        
        return Expr.Type(location, type)
    }
    
    private fun array(): Expr.Array {
        val location = here()
        
        mustSkip(LeftSquare)
        
        val elements = mutableListOf<Expr>()
        
        if (!skip(RightSquare)) {
            do {
                elements += expr()
            }
            while (skip(Comma))
            
            mustSkip(RightSquare)
        }
        
        return Expr.Array(location, elements)
    }
    
    private fun data(): Expr.Instantiate {
        val location = here()
        
        mustSkip(LeftBrace)
        
        val elements = mutableListOf<Expr>()
        
        if (!skip(RightBrace)) {
            do {
                if (!match(Comma)) {
                    elements += expr()
                }
            }
            while (skip(Comma))
            
            mustSkip(RightBrace)
        }
        
        return Expr.Instantiate(location, Expr.Name.none, elements)
    }
    
    private fun nested(): Expr {
        mustSkip(LeftParen)
        
        val expr = expr()
        
        mustSkip(RightParen)
        
        return expr
    }
}