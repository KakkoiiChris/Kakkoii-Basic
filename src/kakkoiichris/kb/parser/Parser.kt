package kakkoiichris.kb.parser

import kakkoiichris.kb.lexer.Token
import kakkoiichris.kb.lexer.Token.Type.*
import kakkoiichris.kb.script.DataType
import kakkoiichris.kb.util.KBError
import java.lang.Integer.min
import kotlin.reflect.jvm.internal.impl.types.checker.StrictEqualityTypeChecker

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
        match(EOF)
    
    private fun stmt() =
        when {
            matchAny(LET, VAR) -> decl()
            
            match(DOO)         -> doo()
            
            match(IFF)         -> iff()
            
            match(WHL)         -> whilee()
            
            match(FOR)         -> forr()
            
            match(DAT)         -> data()
            
            match(SBR)         -> sub()
            
            match(BRK)         -> breakk()
            
            match(NXT)         -> next()
            
            match(RET)         -> returnn()
            
            match(YLD)         -> yield()
            
            else               -> expression()
        }
    
    private fun decl(): Stmt.Decl {
        val loc = here()
        
        val constant = skip(LET)
        
        if (!constant) {
            mustSkip(VAR)
        }
        
        val name = name()
        
        val type = if (skip(AAS)) type() else Expr.Type(name.loc, DataType.Inferred)
        
        val expr = if (skip(ASN)) expr() else Expr.None
        
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
    
    private fun doo(): Stmt.Do {
        val loc = here()
        
        mustSkip(DOO)
        
        val body = block(END)
        
        mustSkipAll(END, DOO)
        
        return Stmt.Do(loc, body)
    }
    
    private fun iff(): Stmt.If {
        val loc = here()
        
        mustSkip(IFF)
        
        val branches = mutableListOf<Pair<Expr, Stmt.Block>>()
        
        do {
            val test = expr()
            
            val body = block(ELF, ELS, END)
            
            branches += test to body
        }
        while (skip(ELF))
        
        val elze = if (skip(ELS)) block(END) else Stmt.None
        
        mustSkipAll(END, IFF)
        
        return Stmt.If(loc, branches, elze)
    }
    
    private fun whilee(): Stmt.While {
        val loc = here()
        
        mustSkip(WHL)
        
        val test = expr()
        
        val body = block(END)
        
        mustSkipAll(END, WHL)
        
        return Stmt.While(loc, test, body)
    }
    
    private fun forr(): Stmt {
        val loc = here()
        
        mustSkip(FOR)
        
        val pointer = name()
        
        val type = if (skip(AAS)) type() else Expr.Type(pointer.loc, DataType.Inferred)
        
        return when {
            matchAny(ASN, TOO) -> {
                val start = if (skip(ASN)) expr() else Expr.None
                
                val decl = Stmt.Decl(pointer.loc, false, pointer, type, start)
                
                mustSkip(TOO)
                
                val to = expr()
                
                val step = if (skip(STP)) expr() else 1.toExpr()
                
                val body = block(END)
                
                mustSkipAll(END, FOR)
                
                Stmt.For(loc, decl, to, step, body)
            }
            
            skip(INN)          -> {
                val decl = Stmt.Decl(pointer.loc, false, pointer, type, Expr.None)
                
                val iterable = expr()
                
                val body = block(END)
                
                mustSkipAll(END, FOR)
                
                Stmt.Foreach(loc, decl, iterable, body)
            }
            
            else               -> KBError.forParser("For loop type is invalid; expected '$TOO' or '$INN'!", here())
        }
    }
    
    private fun data(): Stmt.Data {
        val loc = here()
        
        mustSkip(DAT)
        
        val name = name()
        
        val decls = mutableListOf<Stmt.Decl>()
        
        if (!skipAll(END, DAT)) {
            do {
                decls += decl()
            }
            while (skip(SEP))
            
            mustSkipAll(END, DAT)
        }
        
        return Stmt.Data(loc, name, decls)
    }
    
    private fun sub(): Stmt.Sub {
        val loc = here()
        
        mustSkip(SBR)
        
        val name = name()
        
        val params = mutableListOf<Stmt.Decl>()
        
        mustSkip(LPR)
        
        if (!skip(RPR)) {
            do {
                val paramName = name()
                
                mustSkip(AAS)
                
                val paramType = type()
                
                params += Stmt.Decl(name.loc, false, paramName, paramType, Expr.None)
            }
            while (skip(SEP))
            
            mustSkip(RPR)
        }
        
        val type = if (skip(AAS)) type() else DataType.Primitive.VOID.toType()
        
        val body = block(END)
        
        mustSkipAll(END, SBR)
        
        return Stmt.Sub(loc, name, params, type, body)
    }
    
    private fun breakk(): Stmt.Break {
        val loc = here()
        
        mustSkip(BRK)
        
        return Stmt.Break(loc)
    }
    
    private fun next(): Stmt.Next {
        val loc = here()
        
        mustSkip(NXT)
        
        val pointer = name()
        
        return Stmt.Next(loc, pointer)
    }
    
    private fun returnn(): Stmt.Return {
        val loc = here()
        
        mustSkip(RET)
        
        return Stmt.Return(loc)
    }
    
    private fun yield(): Stmt.Yield {
        val loc = here()
        
        mustSkip(YLD)
        
        val expr = expr()
        
        return Stmt.Yield(loc, expr)
    }
    
    private fun expression() =
        Stmt.Expression(expr())
    
    private fun expr(): Expr = assign()
    
    private fun assign(): Expr {
        val expr = disjunction()
        
        return if (matchAny(ASN, CAD, CSB, CML, CDV, CRM)) {
            val op = peek()
            
            mustSkip(op.type)
            
            when (op.type) {
                ASN  -> Expr.Binary(op.loc, op.type, expr, disjunction())
                CAD  -> Expr.Binary(op.loc, ASN, expr, Expr.Binary(op.loc, ADD, expr, disjunction()))
                CSB  -> Expr.Binary(op.loc, ASN, expr, Expr.Binary(op.loc, SUB, expr, disjunction()))
                CML  -> Expr.Binary(op.loc, ASN, expr, Expr.Binary(op.loc, MUL, expr, disjunction()))
                CDV  -> Expr.Binary(op.loc, ASN, expr, Expr.Binary(op.loc, DIV, expr, disjunction()))
                CRM  -> Expr.Binary(op.loc, ASN, expr, Expr.Binary(op.loc, REM, expr, disjunction()))
                else -> TODO("BROKEN ASSIGN")
            }
        }
        else {
            expr
        }
    }
    
    private fun disjunction(): Expr {
        var expr = conjunction()
        
        while (match(ORR)) {
            val op = peek()
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.loc, op.type, expr, conjunction())
        }
        
        return expr
    }
    
    private fun conjunction(): Expr {
        var expr = equality()
        
        while (match(AND)) {
            val op = peek()
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.loc, op.type, expr, equality())
        }
        
        return expr
    }
    
    private fun equality(): Expr {
        var expr = comparison()
        
        while (matchAny(EQU, NEQ)) {
            val op = peek()
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.loc, op.type, expr, comparison())
        }
        
        return expr
    }
    
    private fun comparison(): Expr {
        var expr = typeCheck()
        
        while (matchAny(LSS, LEQ, GRT, GEQ)) {
            val op = peek()
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.loc, op.type, expr, typeCheck())
        }
        
        return expr
    }
    
    private fun typeCheck(): Expr {
        var expr = additive()
        
        while (match(ISS)) {
            val op = peek()
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.loc, op.type, expr, type())
        }
        
        return expr
    }
    
    private fun additive(): Expr {
        var expr = multiplicative()
        
        while (matchAny(ADD, SUB)) {
            val op = peek()
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.loc, op.type, expr, multiplicative())
        }
        
        return expr
    }
    
    private fun multiplicative(): Expr {
        var expr = typeCast()
        
        while (matchAny(MUL, DIV, REM)) {
            val op = peek()
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.loc, op.type, expr, typeCast())
        }
        
        return expr
    }
    
    private fun typeCast(): Expr {
        var expr = prefix()
        
        while (match(AAS)) {
            val op = peek()
            
            mustSkip(op.type)
            
            expr = Expr.Binary(op.loc, op.type, expr, type())
        }
        
        return expr
    }
    
    private fun prefix(): Expr {
        return if (matchAny(SUB, NOT, LEN)) {
            val op = peek()
            
            mustSkip(op.type)
            
            Expr.Unary(op.loc, op.type, prefix())
        }
        else {
            pipeline()
        }
    }
    
    private fun pipeline(): Expr {
        var expr = postfix()
        
        while (matchAny(PIP)) {
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
                
                else           -> TODO("INVALID PIPELINE RIGHT SIDE")
            }
        }
        
        return expr
    }
    
    private fun postfix(): Expr {
        var expr = terminal()
        
        while (matchAny(DOT, LSQ, LPR, LBC)) {
            val op = peek()
            
            mustSkip(op.type)
            
            expr = when (op.type) {
                DOT  -> Expr.Binary(op.loc, op.type, expr, terminal())
                
                LSQ  -> {
                    val indices = mutableListOf<Expr>()
                    
                    do {
                        indices += expr()
                    }
                    while (skip(SEP))
                    
                    mustSkip(RSQ)
                    
                    var subExpr = expr
                    
                    for (index in indices) {
                        subExpr = Expr.Get(index.loc, subExpr, index)
                    }
                    
                    if (matchAny(ASN, CAD, CSB, CML, CDV, CRM)) {
                        val subOp = peek()
                        
                        mustSkip(subOp.type)
                        
                        val top = subExpr as Expr.Get
                        
                        val loc = top.loc
                        val target = top.target
                        val index = top.index
                        
                        subExpr = when (subOp.type) {
                            ASN -> Expr.Set(loc, target, index, expr())
                            
                            CAD -> Expr.Set(loc,
                                target,
                                index,
                                Expr.Binary(subOp.loc, ADD, Expr.Get(loc, target, index), expr()))
                            
                            CSB -> Expr.Set(loc,
                                target,
                                index,
                                Expr.Binary(subOp.loc, SUB, Expr.Get(loc, target, index), expr()))
                            
                            CML -> Expr.Set(loc,
                                target,
                                index,
                                Expr.Binary(subOp.loc, MUL, Expr.Get(loc, target, index), expr()))
                            
                            CDV -> Expr.Set(loc,
                                target,
                                index,
                                Expr.Binary(subOp.loc, DIV, Expr.Get(loc, target, index), expr()))
                            
                            CRM -> Expr.Set(loc,
                                target,
                                index,
                                Expr.Binary(subOp.loc, REM, Expr.Get(loc, target, index), expr()))
                            
                            else-> TODO("SET COMPOUND ERROR")
                        }
                    }
                    
                    subExpr
                }
                
                LPR  -> {
                    val args = mutableListOf<Expr>()
                    
                    if (!skip(RPR)) {
                        do {
                            args += expr()
                        }
                        while (skip(SEP))
                        
                        mustSkip(RPR)
                    }
                    
                    Expr.Invoke(op.loc, expr as? Expr.Name ?: TODO("NOT A NAME INVOKE"), args)
                }
                
                LBC  -> {
                    val elements = mutableListOf<Expr>()
                    
                    if (!skip(RBC)) {
                        do {
                            elements += expr()
                        }
                        while (skip(SEP))
                        
                        mustSkip(RBC)
                    }
                    
                    Expr.Instantiate(op.loc,
                        expr as? Expr.Name ?: TODO("INSTANTIATE TARGET NOT NAME '$expr'"),
                        elements)
                }
                
                else -> KBError.forParser("Broken postfix operator '${op.type}'!", op.loc)
            }
        }
        
        return expr
    }
    
    private fun terminal(): Expr {
        return when {
            match(VAL) -> value()
            
            match(NAM) -> name()
            
            match(LBC) -> array()
            
            match(LPR) -> nested()
            
            else       -> KBError.forParser("Terminal expression beginning with '${peek().type}' is invalid!",
                here())
        }
    }
    
    private fun value(): Expr.Value {
        val token = peek()
        
        mustSkip(VAL)
        
        return Expr.Value(token.loc, token.value)
    }
    
    private fun name(): Expr.Name {
        val token = peek()
        
        mustSkip(NAM)
        
        return Expr.Name(token.loc, token.value as String)
    }
    
    private fun type(): Expr.Type {
        val loc = here()
        
        var type = when {
            match(NAM) -> DataType.Data(name())
            
            else       -> {
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
        
        while (skip(LSQ)) {
            when {
                skip(RSQ) -> type = DataType.Array(type)
                
                else      -> {
                    do {
                        val initSize = if (!matchAny(SEP, RSQ)) expr() else (-1).toExpr()
                        
                        type = DataType.Array(type, initSize)
                    }
                    while (skip(SEP))
                    
                    mustSkip(RSQ)
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
        
        if (skip(MUL)) {
            type = DataType.Vararg(type)
        }
        
        return Expr.Type(loc, type)
    }
    
    private fun array(): Expr.Array {
        val loc = here()
        
        mustSkip(LBC)
        
        val elements = mutableListOf<Expr>()
        
        if (!skip(RBC)) {
            do {
                elements += expr()
            }
            while (skip(SEP))
            
            mustSkip(RBC)
        }
        
        return Expr.Array(loc, elements)
    }
    
    private fun nested(): Expr {
        mustSkip(LPR)
        
        val expr = expr()
        
        mustSkip(RPR)
        
        return expr
    }
}