package kakkoiichris.kb.parser

import kakkoiichris.kb.lexer.Token
import kakkoiichris.kb.lexer.Token.Type.*
import kakkoiichris.kb.script.DataType
import kakkoiichris.kb.util.KBError
import java.lang.Integer.min

class Parser(private val tokens: List<Token>) {
    private var pos = 0
    
    fun parse(): List<Stmt> {
        val stmts = mutableListOf<Stmt>()
        
        while (!eof()) {
            stmts += stmt()
        }
        
        return stmts
    }
    
    private fun peek(offset: Int = 0) =
        tokens[min(pos + offset, tokens.lastIndex)]
    
    private fun here() =
        peek().loc
    
    private fun step(count: Int = 1) {
        pos += count
    }
    
    private fun match(type: Token.Type, offset: Int = 0) =
        peek(offset).type == type
    
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
    
    private fun skipAll(vararg types: Token.Type): Boolean {
        for ((i, type) in types.withIndex()) {
            if (!match(type, i)) {
                return false
            }
        }
        
        step(types.size)
        
        return true
    }
    
    private fun mustSkip(type: Token.Type) {
        if (!skip(type)) {
            KBError.forParser("Token type '${peek().type}' is invalid; expected '$type'!", here())
        }
    }
    
    private fun mustSkipAll(vararg types: Token.Type) {
        if (!skipAll(*types)) {
            KBError.forParser("Token types beginning with '${peek().type}' are invalid; expected '${types.joinToString()}'!",
                here())
        }
    }
    
    private fun eof() =
        match(EndOfFile)
    
    private fun stmt() =
        when {
            matchAny(Let, Var) -> declStmt()
            
            match(Do)          -> doStmt()
            
            match(If)          -> ifStmt()
            
            match(While)       -> whileStmt()
            
            match(For)         -> forStmt()
            
            match(Data)        -> dataStmt()
            
            match(Sub)         -> subStmt()
            
            match(Break)       -> breakStmt()
            
            match(Next)        -> nextStmt()
            
            match(Return)      -> returnStmt()
            
            match(Yield)       -> yieldStmt()
            
            else               -> expressionStmt()
        }
    
    private fun declStmt(): Stmt.Decl {
        val loc = here()
        
        val constant = skip(Let)
        
        if (!constant) {
            mustSkip(Var)
        }
        
        val name = name()
        
        val type = if (skip(As)) type() else Expr.Type(name.loc, DataType.Inferred)
        
        val expr = if (skip(EqualSign)) expr() else Expr.None
        
        return Stmt.Decl(loc, constant, name, type, expr)
    }
    
    private fun block(vararg ends: Token.Type): Stmt.Block {
        val loc = here()
        
        val stmts = mutableListOf<Stmt>()
        
        while (!matchAny(*ends)) {
            stmts += stmt()
        }
        
        return Stmt.Block(loc, stmts)
    }
    
    private fun doStmt(): Stmt.Do {
        val loc = here()
        
        mustSkip(Do)
        
        val body = block(End)
        
        mustSkipAll(End, Do)
        
        return Stmt.Do(loc, body)
    }
    
    private fun ifStmt(): Stmt.If {
        val loc = here()
        
        mustSkip(If)
        
        val branches = mutableListOf<Pair<Expr, Stmt.Block>>()
        
        do {
            val test = expr()
            
            val body = block(Elif, Else, End)
            
            branches += test to body
        }
        while (skip(Elif))
        
        val elze = if (skip(Else)) block(End) else Stmt.None
        
        mustSkipAll(End, If)
        
        return Stmt.If(loc, branches, elze)
    }
    
    private fun whileStmt(): Stmt.While {
        val loc = here()
        
        mustSkip(While)
        
        val test = expr()
        
        val body = block(End)
        
        mustSkipAll(End, While)
        
        return Stmt.While(loc, test, body)
    }
    
    private fun forStmt(): Stmt {
        val loc = here()
        
        mustSkip(For)
        
        val pointer = name()
        
        val type = if (skip(As)) type() else Expr.Type(pointer.loc, DataType.Inferred)
        
        return when {
            matchAny(EqualSign, To) -> {
                val start = if (skip(EqualSign)) expr() else Expr.None
                
                val decl = Stmt.Decl(pointer.loc, false, pointer, type, start)
                
                mustSkip(To)
                
                val to = expr()
                
                val step = if (skip(Step)) expr() else 1.toExpr()
                
                val body = block(End)
                
                mustSkipAll(End, For)
                
                Stmt.For(loc, decl, to, step, body)
            }
            
            skip(In)                -> {
                val decl = Stmt.Decl(pointer.loc, false, pointer, type, Expr.None)
                
                val iterable = expr()
                
                val body = block(End)
                
                mustSkipAll(End, For)
                
                Stmt.Foreach(loc, decl, iterable, body)
            }
            
            else                    -> KBError.forParser("For loop type is invalid; expected '$To' or '$In'!", here())
        }
    }
    
    private fun dataStmt(): Stmt.Data {
        val loc = here()
        
        mustSkip(Data)
        
        val name = name()
        
        val decls = mutableListOf<Stmt.Decl>()
        
        if (!skipAll(End, Data)) {
            do {
                decls += declStmt()
            }
            while (skip(Comma))
            
            mustSkipAll(End, Data)
        }
        
        return Stmt.Data(loc, name, decls)
    }
    
    private fun subStmt(): Stmt.Sub {
        val loc = here()
        
        mustSkip(Sub)
        
        val name = name()
        
        val params = mutableListOf<Stmt.Decl>()
        
        mustSkip(LeftParen)
        
        if (!skip(RightParen)) {
            do {
                val paramName = name()
                
                mustSkip(As)
                
                val paramType = type()
                
                params += Stmt.Decl(name.loc, false, paramName, paramType, Expr.None)
            }
            while (skip(Comma))
            
            mustSkip(RightParen)
        }
        
        val type = if (skip(As)) type() else DataType.Primitive.VOID.toType()
        
        val body = block(End)
        
        mustSkipAll(End, Sub)
        
        return Stmt.Sub(loc, name, params, type, body)
    }
    
    private fun breakStmt(): Stmt.Break {
        val loc = here()
        
        mustSkip(Break)
        
        return Stmt.Break(loc)
    }
    
    private fun nextStmt(): Stmt.Next {
        val loc = here()
        
        mustSkip(Next)
        
        val pointer = name()
        
        return Stmt.Next(loc, pointer)
    }
    
    private fun returnStmt(): Stmt.Return {
        val loc = here()
        
        mustSkip(Return)
        
        return Stmt.Return(loc)
    }
    
    private fun yieldStmt(): Stmt.Yield {
        val loc = here()
        
        mustSkip(Yield)
        
        val expr = expr()
        
        return Stmt.Yield(loc, expr)
    }
    
    private fun expressionStmt() =
        Stmt.Expression(expr())
    
    private fun expr(): Expr = assign()
    
    private fun assign(): Expr {
        val expr = disjunction()
        
        return if (matchAny(EqualSign, PlusEqual, MinusEqual, StarEqual, SlashEqual, PercentEqual)) {
            val op = peek()
            
            mustSkip(op.type)
            
            when (op.type) {
                PlusEqual    -> Expr.Binary(op.loc,
                    Expr.Binary.Operator.Assign,
                    expr,
                    Expr.Binary(op.loc, Expr.Binary.Operator.Add, expr, disjunction()))
                
                MinusEqual   -> Expr.Binary(op.loc,
                    Expr.Binary.Operator.Assign,
                    expr,
                    Expr.Binary(op.loc, Expr.Binary.Operator.Subtract, expr, disjunction()))
                
                StarEqual    -> Expr.Binary(op.loc,
                    Expr.Binary.Operator.Assign,
                    expr,
                    Expr.Binary(op.loc, Expr.Binary.Operator.Multiply, expr, disjunction()))
                
                SlashEqual   -> Expr.Binary(op.loc,
                    Expr.Binary.Operator.Assign,
                    expr,
                    Expr.Binary(op.loc, Expr.Binary.Operator.Divide, expr, disjunction()))
                
                PercentEqual -> Expr.Binary(op.loc,
                    Expr.Binary.Operator.Assign,
                    expr,
                    Expr.Binary(op.loc, Expr.Binary.Operator.Modulus, expr, disjunction()))
                
                else         -> Expr.Binary(op.loc, Expr.Binary.Operator[op.type], expr, disjunction())
            }
        }
        else {
            expr
        }
    }
    
    private fun disjunction(): Expr {
        var expr = conjunction()
        
        while (match(Or)) {
            val op = peek()
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.loc, Expr.Binary.Operator[op.type], expr, conjunction())
        }
        
        return expr
    }
    
    private fun conjunction(): Expr {
        var expr = equality()
        
        while (match(And)) {
            val op = peek()
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.loc, Expr.Binary.Operator[op.type], expr, equality())
        }
        
        return expr
    }
    
    private fun equality(): Expr {
        var expr = comparison()
        
        while (matchAny(DoubleEqual, LessGreater)) {
            val op = peek()
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.loc, Expr.Binary.Operator[op.type], expr, comparison())
        }
        
        return expr
    }
    
    private fun comparison(): Expr {
        var expr = typeCheck()
        
        while (matchAny(LessSign, LessEqualSign, GreaterSign, GreaterEqualSign)) {
            val op = peek()
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.loc, Expr.Binary.Operator[op.type], expr, typeCheck())
        }
        
        return expr
    }
    
    private fun typeCheck(): Expr {
        var expr = additive()
        
        while (match(Is)) {
            val op = peek()
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.loc, Expr.Binary.Operator[op.type], expr, type())
        }
        
        return expr
    }
    
    private fun additive(): Expr {
        var expr = multiplicative()
        
        while (matchAny(Plus, Minus)) {
            val op = peek()
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.loc, Expr.Binary.Operator[op.type], expr, multiplicative())
        }
        
        return expr
    }
    
    private fun multiplicative(): Expr {
        var expr = typeCast()
        
        while (matchAny(Star, Slash, Percent)) {
            val op = peek()
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.loc, Expr.Binary.Operator[op.type], expr, typeCast())
        }
        
        return expr
    }
    
    private fun typeCast(): Expr {
        var expr = prefix()
        
        while (match(As)) {
            val op = peek()
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.loc, Expr.Binary.Operator[op.type], expr, type())
        }
        
        return expr
    }
    
    private fun prefix(): Expr {
        return if (matchAny(Minus, Not, Pound)) {
            val op = peek()
            
            mustSkip(op.type)
            
            Expr.Unary(op.loc, Expr.Unary.Operator[op.type], prefix())
        }
        else {
            pipeline()
        }
    }
    
    private fun pipeline(): Expr {
        var expr = postfix()
        
        while (matchAny(Colon)) {
            val op = peek()
            
            mustSkip(op.type)
            
            val right = postfix()
            
            expr = when (right) {
                is Expr.Invoke -> {
                    val args = mutableListOf(expr)
                    
                    args.addAll(right.args)
                    
                    Expr.Invoke(op.loc, right.name, args)
                }
                
                is Expr.Name   -> Expr.Invoke(op.loc, right, listOf(expr))
                
                else           -> KBError.forParser("Right side of pipe operator must be a function name or invocation!",
                    right.loc)
            }
        }
        
        return expr
    }
    
    private fun postfix(): Expr {
        var expr = terminal()
        
        while (matchAny(Dot, LeftSquare, LeftParen, LeftBrace)) {
            val op = peek()
            
            mustSkip(op.type)
            
            expr = when (op.type) {
                Dot        -> Expr.Binary(op.loc, Expr.Binary.Operator[op.type], expr, terminal())
                
                LeftSquare -> {
                    val indices = mutableListOf<Expr>()
                    
                    do {
                        indices += expr()
                    }
                    while (skip(Comma))
                    
                    mustSkip(RightSquare)
                    
                    var subExpr = expr
                    
                    for (index in indices) {
                        subExpr = Expr.Get(index.loc, subExpr, index)
                    }
                    
                    if (matchAny(EqualSign, PlusEqual, MinusEqual, StarEqual, SlashEqual, PercentEqual)) {
                        val subOp = peek()
                        
                        mustSkip(subOp.type)
                        
                        val top = subExpr as Expr.Get
                        
                        val loc = top.loc
                        val target = top.target
                        val index = top.index
                        
                        subExpr = when (subOp.type) {
                            PlusEqual    -> Expr.Set(loc,
                                target,
                                index,
                                Expr.Binary(subOp.loc, Expr.Binary.Operator.Add, Expr.Get(loc, target, index), expr()))
                            
                            MinusEqual   -> Expr.Set(loc,
                                target,
                                index,
                                Expr.Binary(subOp.loc,
                                    Expr.Binary.Operator.Subtract,
                                    Expr.Get(loc, target, index),
                                    expr()))
                            
                            StarEqual    -> Expr.Set(loc,
                                target,
                                index,
                                Expr.Binary(subOp.loc,
                                    Expr.Binary.Operator.Multiply,
                                    Expr.Get(loc, target, index),
                                    expr()))
                            
                            SlashEqual   -> Expr.Set(loc,
                                target,
                                index,
                                Expr.Binary(subOp.loc,
                                    Expr.Binary.Operator.Divide,
                                    Expr.Get(loc, target, index),
                                    expr()))
                            
                            PercentEqual -> Expr.Set(loc,
                                target,
                                index,
                                Expr.Binary(subOp.loc,
                                    Expr.Binary.Operator.Modulus,
                                    Expr.Get(loc, target, index),
                                    expr()))
                            
                            else         -> Expr.Set(loc, target, index, expr())
                        }
                    }
                    
                    subExpr
                }
                
                LeftParen  -> {
                    val args = mutableListOf<Expr>()
                    
                    if (!skip(RightParen)) {
                        do {
                            args += expr()
                        }
                        while (skip(Comma))
                        
                        mustSkip(RightParen)
                    }
                    
                    Expr.Invoke(op.loc,
                        expr as? Expr.Name ?: KBError.forParser("Invoke left side must be a variable name!", expr.loc),
                        args)
                }
                
                LeftBrace  -> {
                    val elements = mutableListOf<Expr>()
                    
                    if (!skip(RightBrace)) {
                        do {
                            elements += expr()
                        }
                        while (skip(Comma))
                        
                        mustSkip(RightBrace)
                    }
                    
                    Expr.Instantiate(op.loc,
                        expr as? Expr.Name ?: KBError.forParser("Instantiation left side must be a variable name!",
                            expr.loc),
                        elements)
                }
                
                else       -> KBError.forParser("Broken postfix operator '${op.type}'!", op.loc)
            }
        }
        
        return expr
    }
    
    private fun terminal(): Expr {
        return when {
            match(Value)     -> value()
            
            match(Word)      -> name()
            
            match(LeftBrace) -> array()
            
            match(LeftParen) -> nested()
            
            else             -> KBError.forParser("Terminal expression beginning with '${peek().type}' is invalid!",
                here())
        }
    }
    
    private fun value(): Expr.Value {
        val token = peek()
        
        mustSkip(Value)
        
        return Expr.Value(token.loc, token.value)
    }
    
    private fun name(): Expr.Name {
        val token = peek()
        
        mustSkip(Word)
        
        return Expr.Name(token.loc, token.value as String)
    }
    
    private fun type(): Expr.Type {
        val loc = here()
        
        var type = when {
            match(Word) -> DataType.Data(name())
            
            else        -> {
                val token = peek()
                
                val primitive = DataType
                    .Primitive
                    .values()
                    .firstOrNull {
                        token
                            .type
                            .toString()
                            .equals(it.name, true)
                    } ?: KBError.forParser("Token type '${token.type}' is not a valid base data type!", token.loc)
                
                mustSkip(token.type)
                
                primitive
            }
        }
        
        while (skip(LeftSquare)) {
            when {
                skip(RightSquare) -> type = DataType.Array(type)
                
                else              -> {
                    do {
                        val initSize = if (!matchAny(Comma, RightSquare)) expr() else (-1).toExpr()
                        
                        type = DataType.Array(type, initSize)
                    }
                    while (skip(Comma))
                    
                    mustSkip(RightSquare)
                }
            }
        }
        
        if (type is DataType.Array) {
            val initSizes = mutableListOf<Expr>()
            
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
        
        return Expr.Type(loc, type)
    }
    
    private fun array(): Expr.Array {
        val loc = here()
        
        mustSkip(LeftBrace)
        
        val elements = mutableListOf<Expr>()
        
        if (!skip(RightBrace)) {
            do {
                elements += expr()
            }
            while (skip(Comma))
            
            mustSkip(RightBrace)
        }
        
        return Expr.Array(loc, elements)
    }
    
    private fun nested(): Expr {
        mustSkip(LeftParen)
        
        val expr = expr()
        
        mustSkip(RightParen)
        
        return expr
    }
}