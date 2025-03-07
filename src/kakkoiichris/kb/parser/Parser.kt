package kakkoiichris.kb.parser

import kakkoiichris.kb.lexer.Context
import kakkoiichris.kb.lexer.Lexer
import kakkoiichris.kb.lexer.Token
import kakkoiichris.kb.lexer.Token.Type.*
import kakkoiichris.kb.runtime.DataType
import kakkoiichris.kb.util.KBError

class Parser(private val lexer: Lexer) {
    private var currentToken = lexer.next()
    private var lastToken = currentToken

    fun parse(): List<Stmt> {
        val stmts = mutableListOf<Stmt>()

        while (!isEndOfFile()) {
            stmts += stmt()
        }

        return stmts
    }

    private fun context() =
        currentToken.context

    private fun step() {
        if (lexer.hasNext()) {
            lastToken = currentToken
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
            KBError.invalidTokenType(currentToken.type, type, context())
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

                else         -> KBError.nonLabeledStatement(currentToken.type, context())
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
        val startContext = context()

        val constant = skip(LET)

        if (!constant) {
            mustSkip(VAR)
        }

        val each = skip(EACH)

        val definitions = mutableListOf<Definition>()

        if (each) {
            do {
                definitions += if (!match(COMMA)) {
                    val name = name()

                    val type = if (skip(AS)) type() else Expr.Type.none

                    Definition(name, type)
                }
                else Definition.none
            }
            while (skip(COMMA))
        }
        else {
            val name = name()

            val type = if (skip(AS)) type() else Expr.Type.none

            definitions += Definition(name, type)
        }

        val expr = if (skip(EQUAL_SIGN)) expr() else Expr.Empty

        if (each) {
            val endContext = expr.context

            val context = startContext + endContext

            return Stmt.DeclEach(context, constant, definitions, expr)
        }

        val definition = definitions[0]

        val endContext = expr.context

        val context = startContext + endContext

        return Stmt.Decl(context, constant, definition, expr)
    }

    private fun block(vararg ends: Token.Type): Stmt.Block {
        val startContext = context()

        val stmts = mutableListOf<Stmt>()

        while (!matchAny(*ends)) {
            stmts += stmt()
        }

        val endContext = context()

        val context = startContext + endContext

        return Stmt.Block(context, stmts)
    }

    private fun doStmt(label: Expr.Name): Stmt.Do {
        val startContext = context()

        mustSkip(DO)

        val body = block(END)

        mustSkip(END)

        val endContext = context()

        mustSkip(DO)

        val context = startContext + endContext

        return Stmt.Do(context, label, body)
    }

    private fun ifStmt(): Stmt.If {
        val startContext = context()

        mustSkip(IF)

        val test = expr()

        val body = block(ELSE, END)

        val `else` = if (skip(ELSE)) {
            if (match(IF)) {
                elseIfStmt()
            }
            else {
                block(END)
            }
        }
        else Stmt.None

        mustSkip(END)

        val endContext = context()

        mustSkip(IF)

        val context = startContext + endContext

        return Stmt.If(context, test, body, `else`)
    }

    private fun elseIfStmt(): Stmt.If {
        val startContext = context()

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

        val endContext = context()

        val context = startContext + endContext

        return Stmt.If(context, test, body, elze)
    }

    private fun switchStmt(): Stmt.Switch {
        val startContext = context()

        mustSkip(SWITCH)

        val subject = expr()

        val cases = mutableListOf<Stmt.Switch.Case>()

        var `else`: Stmt.Switch.Case.Else? = null

        while (!match(END)) {
            val caseStartContext = context()

            mustSkip(CASE)

            when {
                skip(ELSE) -> if (`else` == null) {
                    val block = block(END)

                    mustSkip(END)

                    val caseContext = caseStartContext + context()

                    mustSkip(CASE)

                    `else` = Stmt.Switch.Case.Else(caseContext, block)
                }
                else {
                    KBError.duplicateElseCase(context())
                }

                skip(IS)   -> {
                    val inverted = skip(NOT)

                    val type = type()

                    val block = block(END)

                    mustSkip(END)

                    val caseContext = caseStartContext + context()

                    mustSkip(CASE)

                    cases += Stmt.Switch.Case.Type(caseContext, inverted, type, block)
                }

                else       -> {
                    val values = mutableListOf<Expr.Value>()

                    do {
                        values += value()
                    }
                    while (skip(COMMA))

                    val block = block(END)

                    mustSkip(END)

                    val caseContext = caseStartContext + context()

                    mustSkip(CASE)

                    cases += Stmt.Switch.Case.Values(caseContext, values, block)
                }
            }
        }

        mustSkip(END)

        val context = startContext + context()

        mustSkip(SWITCH)

        if (`else` != null) {
            cases += `else`
        }

        return Stmt.Switch(context, subject, cases)
    }

    private fun whileStmt(label: Expr.Name): Stmt.While {
        val startContext = context()

        mustSkip(WHILE)

        val test = expr()

        val body = block(END)

        mustSkip(END)

        val endContext = context()

        mustSkip(WHILE)

        val context = startContext + endContext

        return Stmt.While(context, label, test, body)
    }

    private fun untilStmt(label: Expr.Name): Stmt.Until {
        val startContext = context()

        mustSkip(UNTIL)

        val test = expr()

        val body = block(END)

        mustSkip(END)

        val endContext = context()

        mustSkip(UNTIL)

        val context = startContext + endContext

        return Stmt.Until(context, label, test, body)
    }

    private fun forStmt(label: Expr.Name): Stmt {
        val startContext = context()

        mustSkip(FOR)

        val each = skip(EACH)

        val definitions = mutableListOf<Definition>()

        if (each) {
            do {
                definitions += if (!match(COMMA)) {
                    val name = name()

                    val type = if (skip(AS)) type() else Expr.Type.none

                    Definition(name, type)
                }
                else {
                    Definition.none
                }
            }
            while (skip(COMMA))
        }
        else {
            val name = name()

            val type = if (skip(AS)) type() else Expr.Type.none

            definitions += Definition(name, type)
        }

        return when {
            each                     -> {
                mustSkip(IN)

                val decl = Stmt.DeclEach(definitions[0].name.context, false, definitions, Expr.Empty)

                val iterable = expr()

                val body = block(END)

                mustSkip(END)

                val endContext = context()

                mustSkip(FOR)

                val context = startContext + endContext

                Stmt.ForIterateEach(context, label, decl, iterable, body)
            }

            skip(IN)                 -> {
                val definition = definitions[0]

                val decl = Stmt.Decl(definition.name.context, false, definition, Expr.Empty)

                val iterable = expr()

                val body = block(END)

                mustSkip(END)

                val endContext = context()

                mustSkip(FOR)

                val context = startContext + endContext

                Stmt.ForIterate(context, label, decl, iterable, body)
            }

            matchAny(EQUAL_SIGN, TO) -> {
                val start = if (skip(EQUAL_SIGN)) expr() else Expr.Empty

                val definition = definitions[0]

                val decl = Stmt.Decl(definition.name.context, false, definition, start)

                mustSkip(TO)

                val to = expr()

                val step = if (skip(STEP)) expr() else 1.toExpr()

                val body = block(END)

                mustSkip(END)

                val endContext = context()

                mustSkip(FOR)

                val context = startContext + endContext

                Stmt.ForCounter(context, label, decl, to, step, body)
            }

            else                     -> KBError.invalidForLoop(startContext)
        }
    }

    private fun dataStmt(): Stmt.Data {
        val startContext = context()

        mustSkip(DATA)

        val name = name()

        val decls = mutableListOf<Stmt.Decl>()

        if (!match(END)) {
            do {
                val declStartContext = context()
                var declEndContext = Context.none

                val constant = skip(LET)

                if (!constant) {
                    mustSkip(VAR)
                }

                val declName = name()

                var declType = Expr.Type.none

                if (skip(AS)) {
                    declType = type()

                    declEndContext = declType.context
                }

                val definition = Definition(declName, declType)

                var expr: Expr = Expr.Empty

                if (skip(EQUAL_SIGN)) {
                    expr = expr()

                    declEndContext = expr.context
                }

                val declContext = declStartContext + declEndContext

                decls += Stmt.Decl(declContext, constant, definition, expr)
            }
            while (skip(COMMA))
        }

        mustSkip(END)

        val endContext = context()

        mustSkip(DATA)

        val context = startContext + endContext

        return Stmt.Data(context, name, decls)
    }

    private fun subStmt(): Stmt.Sub {
        val startContext = context()

        mustSkip(SUB)

        val name = name()

        val type = if (skip(AS)) type() else DataType.Primitive.NONE.toType()

        val definition = Definition(name, type)

        val params = mutableListOf<Stmt.Decl>()

        if (skip(WITH)) {
            do {
                val paramName = name()

                mustSkip(AS)

                val paramType = type()

                val paramDefinition = Definition(paramName, paramType)

                val expr = if (skip(EQUAL_SIGN)) expr() else Expr.Empty

                params += Stmt.Decl(name.context, false, paramDefinition, expr)
            }
            while (skip(COMMA))
        }

        val body = block(END)

        mustSkip(END)

        val endContext = context()

        mustSkip(SUB)

        val context = startContext + endContext

        return Stmt.Sub(context, definition, params, body)
    }

    private fun breakStmt(): Stmt.Break {
        val startContext = context()

        mustSkip(BREAK)

        val destination = if (skip(TO)) name() else Expr.Name.none

        val endContext = destination.context

        val context = startContext + endContext

        return Stmt.Break(context, destination)
    }

    private fun nextStmt(): Stmt.Next {
        val startContext = context()

        mustSkip(NEXT)

        val destination = if (skip(TO)) name() else Expr.Name.none

        val endContext = destination.context

        val context = startContext + endContext

        return Stmt.Next(context, destination)
    }

    private fun returnStmt(): Stmt.Return {
        val context = context()

        mustSkip(RETURN)

        return Stmt.Return(context)
    }

    private fun yieldStmt(): Stmt.Yield {
        val startContext = context()

        mustSkip(YIELD)

        val expr = expr()

        val endContext = expr.context

        val context = startContext + endContext

        return Stmt.Yield(context, expr)
    }

    private fun typeStmt(): Stmt.Type {
        val startContext = context()

        mustSkip(TYPE)

        val full = type()

        mustSkip(AS)

        val alias = name()

        val endContext = alias.context

        val context = startContext + endContext

        return Stmt.Type(context, full, alias)
    }

    private fun enumStmt(): Stmt {
        val startContext = context()

        mustSkip(ENUM)

        val name = name()

        return if (match(AS)) {
            dataEnum(startContext, name)
        }
        else {
            basicEnum(startContext, name)
        }
    }

    private fun basicEnum(startContext: Context, name: Expr.Name): Stmt.BasicEnum {
        val entries = mutableListOf<Stmt.BasicEnum.Entry>()

        var ordinal = 0

        do {
            val entryName = name()

            val value = if (skip(EQUAL_SIGN)) value() else ordinal.toExpr()

            entries += Stmt.BasicEnum.Entry(entryName.context, entryName, ordinal.toExpr(), value)

            ordinal++
        }
        while (skip(COMMA))

        mustSkip(END)

        val endContext = context()

        mustSkip(ENUM)

        val context = startContext + endContext

        return Stmt.BasicEnum(context, name, entries)
    }

    private fun dataEnum(startContext: Context, name: Expr.Name): Stmt.DataEnum {
        val type = if (skip(AS)) type() else Expr.Type(name.context, DataType.Primitive.INT)

        val entries = mutableListOf<Stmt.DataEnum.Entry>()

        var ordinal = 0

        do {
            val entryName = name()

            if (!skip(EQUAL_SIGN)) TODO("DATA ENUM ENTRY MUST HAVE INSTANTIATE")

            val value = if (match(LEFT_BRACE)) data() else instantiate()

            entries += Stmt.DataEnum.Entry(entryName.context, entryName, ordinal.toExpr(), value)

            ordinal++
        }
        while (skip(COMMA))

        mustSkip(END)

        val endContext = context()

        mustSkip(ENUM)

        val context = startContext + endContext

        return Stmt.DataEnum(context, name, type, entries)
    }

    private fun expressionStmt() =
        Stmt.Expression(expr())

    private fun expr() = assign()

    private fun assign(): Expr {
        val expr = disjunction()

        if (!matchAny(EQUAL_SIGN, PLUS_EQUAL, DASH_EQUAL, STAR_EQUAL, SLASH_EQUAL, PERCENT_EQUAL, AMPERSAND_EQUAL)) {
            return expr
        }

        val op = currentToken

        mustSkip(op.type)

        fun desugar(newOp: Expr.Binary.Operator): Expr.Binary {
            val right = disjunction()

            val context = expr.context + right.context

            return Expr.Binary(context, Expr.Binary.Operator.ASSIGN, expr, Expr.Binary(context, newOp, expr, right))
        }

        return when (op.type) {
            PLUS_EQUAL      -> desugar(Expr.Binary.Operator.ADD)

            DASH_EQUAL      -> desugar(Expr.Binary.Operator.SUBTRACT)

            STAR_EQUAL      -> desugar(Expr.Binary.Operator.MULTIPLY)

            SLASH_EQUAL     -> desugar(Expr.Binary.Operator.DIVIDE)

            PERCENT_EQUAL   -> desugar(Expr.Binary.Operator.MODULUS)

            AMPERSAND_EQUAL -> desugar(Expr.Binary.Operator.CONCAT)

            else            -> {
                val right = disjunction()

                val context = expr.context + right.context

                Expr.Binary(context, Expr.Binary.Operator[op.type], expr, right)
            }
        }
    }

    private fun disjunction(): Expr {
        var expr = conjunction()

        while (match(OR)) {
            val op = currentToken

            mustSkip(op.type)

            val right = conjunction()

            val context = expr.context + right.context

            expr = Expr.Binary(context, Expr.Binary.Operator[op.type], expr, right)
        }

        return expr
    }

    private fun conjunction(): Expr {
        var expr = equality()

        while (match(AND)) {
            val op = currentToken

            mustSkip(op.type)

            val right = equality()

            val context = expr.context + right.context

            expr = Expr.Binary(context, Expr.Binary.Operator[op.type], expr, right)
        }

        return expr
    }

    private fun equality(): Expr {
        var expr = comparison()

        while (matchAny(DOUBLE_EQUAL, LESS_GREATER)) {
            val op = currentToken

            mustSkip(op.type)

            val right = comparison()

            val context = expr.context + right.context

            expr = Expr.Binary(context, Expr.Binary.Operator[op.type], expr, right)
        }

        return expr
    }

    private fun comparison(): Expr {
        var expr = typeCheck()

        while (matchAny(LESS_SIGN, LESS_EQUAL_SIGN, GREATER_SIGN, GREATER_EQUAL_SIGN)) {
            val op = currentToken

            mustSkip(op.type)

            val right = typeCheck()

            val context = expr.context + right.context

            expr = Expr.Binary(context, Expr.Binary.Operator[op.type], expr, right)
        }

        return expr
    }

    private fun typeCheck(): Expr {
        var expr = concat()

        while (match(IS)) {
            val op = currentToken

            mustSkip(op.type)

            val opType = if (skip(NOT)) NOT else IS

            val right = type()

            val context = expr.context + right.context

            expr = Expr.Binary(context, Expr.Binary.Operator[opType], expr, right)
        }

        return expr
    }

    private fun concat(): Expr {
        var expr = additive()

        while (match(AMPERSAND)) {
            val op = currentToken

            mustSkip(op.type)

            val right = additive()

            val context = expr.context + right.context

            expr = Expr.Binary(context, Expr.Binary.Operator[op.type], expr, right)
        }

        return expr
    }

    private fun additive(): Expr {
        var expr = multiplicative()

        while (matchAny(PLUS, DASH)) {
            val op = currentToken

            mustSkip(op.type)

            val right = multiplicative()

            val context = expr.context + right.context

            expr = Expr.Binary(context, Expr.Binary.Operator[op.type], expr, right)
        }

        return expr
    }

    private fun multiplicative(): Expr {
        var expr = typeCast()

        while (matchAny(STAR, SLASH, PERCENT)) {
            val op = currentToken

            mustSkip(op.type)

            val right = typeCast()

            val context = expr.context + right.context

            expr = Expr.Binary(context, Expr.Binary.Operator[op.type], expr, right)
        }

        return expr
    }

    private fun typeCast(): Expr {
        var expr = prefix()

        while (match(AS)) {
            val op = currentToken

            mustSkip(op.type)

            val right = type()

            val context = expr.context + right.context

            expr = Expr.Binary(context, Expr.Binary.Operator[op.type], expr, right)
        }

        return expr
    }

    private fun prefix(): Expr {
        return if (matchAny(DASH, NOT, POUND, DOLLAR, AT)) {
            val op = currentToken

            mustSkip(op.type)

            val right = prefix()

            val context = op.context + right.context

            Expr.Unary(context, Expr.Unary.Operator[op.type], right)
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

            val context = expr.context + right.context

            expr = when (right) {
                is Expr.Invoke -> {
                    val args = mutableListOf(Expr.Invoke.Argument(false, expr))

                    args.addAll(right.args)

                    Expr.Invoke(context, right.name, args)
                }

                is Expr.Name   -> Expr.Invoke(context, right, listOf(Expr.Invoke.Argument(false, expr)))

                else           -> KBError.invalidPipelineOperand(right.context)
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

                match(LEFT_PAREN)   -> {
                    if (expr !is Expr.Name) KBError.invalidInvocationTarget(expr.context)

                    invoke(expr)
                }

                match(LEFT_BRACE)   -> {
                    if (expr !is Expr.Name) KBError.invalidInstantiationTarget(expr.context)

                    instantiate(expr)
                }

                match(DOUBLE_COLON) -> {
                    if (expr !is Expr.Name) KBError.invalidEntryTarget(expr.context)

                    entry(expr)
                }

                else                -> KBError.failure(
                    "Broken postfix operator '${currentToken.type}'!",
                    context()
                )
            }
        }

        return expr
    }

    private fun member(expr: Expr): Expr.GetMember {
        mustSkip(DOT)

        val name = name()

        val context = expr.context + name.context

        return Expr.GetMember(context, expr, name)
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
            subExpr = Expr.GetIndex(index.context, subExpr, index)
        }

        if (!matchAny(EQUAL_SIGN, PLUS_EQUAL, DASH_EQUAL, STAR_EQUAL, SLASH_EQUAL, PERCENT_EQUAL)) {
            return subExpr
        }

        val subOp = currentToken

        mustSkip(subOp.type)

        val top = subExpr as Expr.GetIndex

        val context = top.context
        val target = top.target
        val index = top.index

        fun desugar(newOp: Expr.Binary.Operator): Expr.SetIndex {
            val getIndex = Expr.GetIndex(context, target, index)

            val right = expr()

            val indexContext = target.context + right.context

            val binary = Expr.Binary(indexContext, newOp, getIndex, expr())

            return Expr.SetIndex(indexContext, target, index, binary)
        }

        return when (subOp.type) {
            PLUS_EQUAL    -> desugar(Expr.Binary.Operator.ADD)

            DASH_EQUAL    -> desugar(Expr.Binary.Operator.SUBTRACT)

            STAR_EQUAL    -> desugar(Expr.Binary.Operator.MULTIPLY)

            SLASH_EQUAL   -> desugar(Expr.Binary.Operator.DIVIDE)

            PERCENT_EQUAL -> desugar(Expr.Binary.Operator.MODULUS)

            else          -> Expr.SetIndex(context, target, index, expr())
        }
    }

    private fun invoke(name: Expr.Name): Expr.Invoke {
        val startContext = name.context

        mustSkip(LEFT_PAREN)

        val args = mutableListOf<Expr.Invoke.Argument>()

        if (!match(RIGHT_PAREN)) {
            do {
                val each = skip(EACH)

                val argExpr = if (match(COMMA)) Expr.Empty else expr()

                args += Expr.Invoke.Argument(each, argExpr)
            }
            while (skip(COMMA))
        }

        val endContext = context()

        mustSkip(RIGHT_PAREN)

        val context = startContext + endContext

        return Expr.Invoke(context, name, args)
    }

    private fun instantiate(name: Expr.Name = name()): Expr.Instantiate {
        val startContext = name.context

        mustSkip(LEFT_BRACE)

        val elements = mutableListOf<Expr>()

        if (!match(RIGHT_BRACE)) {
            do {
                elements += if (match(COMMA)) Expr.Empty else expr()
            }
            while (skip(COMMA))
        }

        val endContext = context()

        mustSkip(RIGHT_BRACE)

        val context = startContext + endContext

        return Expr.Instantiate(context, name, elements)
    }

    private fun entry(name: Expr.Name): Expr.GetEntry {
        mustSkip(DOUBLE_COLON)

        val end = currentToken.context

        val member = if (skip(Token.Type.STAR))
            Expr.Name(end, "*")
        else
            name()

        val context = name.context + member.context

        return Expr.GetEntry(context, name, member)
    }

    private fun terminal(): Expr {
        return when {
            match(VALUE)       -> value()

            match(WORD)        -> name()

            match(LEFT_SQUARE) -> array()

            match(LEFT_BRACE)  -> data()

            match(LEFT_PAREN)  -> nested()

            else               -> KBError.invalidTerminal(currentToken.type, currentToken.context)
        }
    }

    private fun value(): Expr.Value {
        val token = currentToken

        mustSkip(VALUE)

        return Expr.Value(token.context, token.value)
    }

    private fun name(): Expr.Name {
        val token = currentToken

        mustSkip(WORD)

        return Expr.Name(token.context, token.value as String)
    }

    private fun type(): Expr.Type {
        val startContext = context()
        var endContext = context()

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
                ?: KBError.invalidDataType(token.type, token.context)

            mustSkip(token.type)

            primitive
        }

        while (skip(LEFT_SQUARE)) {
            when {
                match(RIGHT_SQUARE) -> {
                    endContext = context()

                    mustSkip(RIGHT_SQUARE)

                    type = DataType.Array(type)
                }

                else                -> {
                    do {
                        val initSize = if (!matchAny(COMMA, RIGHT_SQUARE)) expr() else null

                        type = DataType.Array(type, initSize)
                    }
                    while (skip(COMMA))

                    endContext = context()

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

        if (match(STAR)) {
            endContext = context()

            mustSkip(STAR)

            type = DataType.Vararg(type)
        }

        val context = startContext + endContext

        return Expr.Type(context, type)
    }

    private fun array(): Expr.Array {
        val startContext = context()
        var endContext: Context

        mustSkip(LEFT_SQUARE)

        val elements = mutableListOf<Expr>()

        endContext = context()

        if (!skip(RIGHT_SQUARE)) {
            do {
                val eachStartContext = context()
                var eachEndContext: Context

                val each = skip(EACH)

                var element = expr()

                if (each) {
                    eachEndContext = element.context

                    val eachContext = eachStartContext + eachEndContext

                    element = Expr.Each(eachContext, element)
                }

                elements += element
            }
            while (skip(COMMA))

            endContext = context()

            mustSkip(RIGHT_SQUARE)
        }

        val context = startContext + endContext

        return Expr.Array(context, elements)
    }

    private fun data(): Expr.Instantiate {
        val startContext = context()
        var endContext: Context

        mustSkip(LEFT_BRACE)

        val elements = mutableListOf<Expr>()

        endContext = context()

        if (!skip(RIGHT_BRACE)) {
            do {
                if (!match(COMMA)) {
                    elements += expr()
                }
            }
            while (skip(COMMA))

            endContext = context()

            mustSkip(RIGHT_BRACE)
        }

        val context = startContext + endContext

        return Expr.Instantiate(context, Expr.Name.none, elements)
    }

    private fun nested(): Expr {
        mustSkip(LEFT_PAREN)

        val expr = expr()

        mustSkip(RIGHT_PAREN)

        return expr
    }
}