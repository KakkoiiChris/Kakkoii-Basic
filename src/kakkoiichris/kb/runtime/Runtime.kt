package kakkoiichris.kb.runtime

import kakkoiichris.kb.lexer.Context
import kakkoiichris.kb.parser.Expr
import kakkoiichris.kb.parser.Stmt
import kakkoiichris.kb.parser.toExpr
import kakkoiichris.kb.parser.toName
import kakkoiichris.kb.util.KBError
import kakkoiichris.kb.util.Source

class Runtime(private val stmts: List<Stmt>) : Stmt.Visitor<Unit>, Expr.Visitor<KBV> {
    val memory = Memory()

    private val global = Memory.Scope("global")

    private val library = StandardLibrary()

    fun run() {
        val coreSource = Source.readLocal("/core.kb")

        val coreStmts = coreSource.compile()

        try {
            memory.push(global)

            for (stmt in coreStmts) {
                visit(stmt)
            }

            for (stmt in stmts) {
                visit(stmt)
            }
        }
        finally {
            memory.pop()
        }
    }

    override fun visit(stmt: Stmt) {
        try {
            super<Stmt.Visitor>.visit(stmt)
        }
        catch (e: KBError) {
            e.push(stmt)

            throw e
        }
    }

    override fun visitNoneStmt(stmt: Stmt.None) =
        Unit

    override fun visitDeclStmt(stmt: Stmt.Decl) {
        if (memory.hasRef(stmt.definition.name)) {
            KBError.alreadyDeclaredVariable(stmt.definition.name, stmt.definition.name.context)
        }

        var expr = stmt.expr
        var type = stmt.definition.type.value

        if (expr is Expr.Instantiate && expr.isInferred && type is DataType.Data) {
            expr = expr.withTarget(type.name)
        }

        var value = visit(expr)

        if (value === KBNone) {
            KBError.assignedNone(stmt.expr.context)
        }

        if (type === DataType.Inferred) {
            if (value is KBInvoke) {
                type = value.type
                value = value.result
            }
            else {
                type = DataType.infer(this, value)
            }
        }

        if (value === KBEmpty) {
            value = type.default(this) ?: KBError.noDefaultValue(type, stmt.expr.context)
        }

        value = type.coerce(value) ?: value

        if (type.filter(this, value) == null) {
            if (type is DataType.Array && type.mismatchedSize(this, value)) {
                KBError.mismatchedArraySize(stmt.expr.context)
            }

            KBError.mismatchedType(value, type, stmt.context)
        }

        memory.newRef(stmt.constant, stmt.definition.name, type, value)
    }

    override fun visitDeclEachStmt(stmt: Stmt.DeclEach) {
        for ((name, _) in stmt.definitions) {
            if (memory.hasRef(name)) {
                KBError.alreadyDeclaredVariable(name, name.context)
            }
        }

        val value = visit(stmt.expr)

        if (value === KBNone) {
            KBError.assignedNone(stmt.expr.context)
        }

        for ((i, pair) in stmt.definitions.withIndex()) {
            var subValue = when (value) {
                is KBString -> KBChar(value.value[i])

                is KBArray  -> value.value[i]

                is KBData   -> value.value.deref()[i]

                else        -> KBError.nonPartitionedType(DataType.infer(this, value), stmt.expr.context)
            }

            val (name, type) = pair

            var subType = type.value

            if (subType === DataType.Inferred) {
                subType = DataType.infer(this, subValue)
            }

            subValue = subType.coerce(subValue) ?: subValue

            if (subType.filter(this, subValue) == null) {
                KBError.mismatchedType(subValue, subType, stmt.context)
            }

            memory.newRef(stmt.constant, name, subType, subValue)
        }
    }

    override fun visitBlockStmt(stmt: Stmt.Block) {
        try {
            memory.push()

            for (subStmt in stmt.stmts) {
                visit(subStmt)
            }
        }
        finally {
            memory.pop()
        }
    }

    override fun visitDoStmt(stmt: Stmt.Do) {
        try {
            visit(stmt.body)
        }
        catch (r: Redirect.Break) {
            if (!(r.label.value.isEmpty() || r.label == stmt.label)) {
                throw r
            }
        }
    }

    override fun visitIfStmt(stmt: Stmt.If) {
        val testValue = visit(stmt.test)

        testValue as? KBBool ?: KBError.invalidTestExpression(testValue, stmt.test.context)

        if (testValue.value) {
            visit(stmt.body)

            return
        }

        visit(stmt.`else`)
    }

    override fun visitSwitchStmt(stmt: Stmt.Switch) {
        val subject = visit(stmt.subject)

        for (case in stmt.cases) {
            try {
                when (case) {
                    is Stmt.Switch.Case.Values -> {
                        for (test in case.tests) {
                            if (subject == visit(test)) {
                                visit(case.block)

                                return
                            }
                        }
                    }

                    is Stmt.Switch.Case.Type   -> {
                        val type = DataType.resolve(this, case.type.value)

                        if ((type.filter(this, subject) == null) xor case.inverted) {
                            continue
                        }

                        visit(case.block)

                        return
                    }

                    is Stmt.Switch.Case.Else   -> visit(case.block)
                }
            }
            catch (r: Redirect.Next) {
                continue
            }
        }
    }

    override fun visitWhileStmt(stmt: Stmt.While) {
        while (true) {
            val testValue = visit(stmt.test)

            testValue as? KBBool ?: KBError.invalidTestExpression(testValue, stmt.test.context)

            if (!testValue.value) break

            try {
                visit(stmt.body)
            }
            catch (r: Redirect.Break) {
                if (!(r.label.value.isEmpty() || r.label == stmt.label)) {
                    throw r
                }

                break
            }
            catch (r: Redirect.Next) {
                continue
            }
        }
    }

    override fun visitUntilStmt(stmt: Stmt.Until) {
        while (true) {
            try {
                visit(stmt.body)
            }
            catch (r: Redirect.Break) {
                if (!(r.label.isEmptyValue() || r.label == stmt.label)) {
                    throw r
                }

                break
            }
            catch (r: Redirect.Next) {
                continue
            }

            val testValue = visit(stmt.test)

            testValue as? KBBool ?: KBError.invalidTestExpression(testValue, stmt.test.context)

            if (testValue.value) break
        }
    }

    override fun visitForCounterStmt(stmt: Stmt.ForCounter) {
        try {
            memory.push("for pointer")

            visit(stmt.decl)

            val pointer = stmt.decl.definition.name

            val test = Expr.Binary(stmt.to.context, Expr.Binary.Operator.LESS, pointer, stmt.to)

            val increment =
                Expr.Assign(
                    stmt.step.context,
                    pointer,
                    Expr.Binary(stmt.step.context, Expr.Binary.Operator.ADD, pointer, stmt.step)
                )

            do {
                try {
                    visit(stmt.body)
                }
                catch (r: Redirect.Break) {
                    if (!(r.label.value.isEmpty() || r.label == stmt.label)) {
                        throw r
                    }

                    break
                }
                catch (r: Redirect.Next) {
                    if (!(r.label.value.isEmpty() || r.label == stmt.label)) {
                        throw r
                    }

                    continue
                }

                visit(increment)
            }
            while ((visit(test) as KBBool).value)
        }
        finally {
            memory.pop()
        }
    }

    override fun visitForIterateStmt(stmt: Stmt.ForIterate) {
        val iterableValue = visit(stmt.iterable)

        val iterableType = DataType.infer(this, iterableValue)

        val iterable =
            iterableType.iterable(this, iterableValue) ?: KBError.nonIterableType(iterableType, stmt.iterable.context)

        for (x in iterable) {
            try {
                memory.push("for iterate pointer")

                val decl = stmt.decl.withValue(x)

                visit(decl)

                try {
                    visit(stmt.body)
                }
                catch (r: Redirect.Break) {
                    if (!(r.label.value.isEmpty() || r.label == stmt.label)) {
                        throw r
                    }

                    break
                }
                catch (r: Redirect.Next) {
                    if (!(r.label.value.isEmpty() || r.label == stmt.label)) {
                        throw r
                    }

                    continue
                }
            }
            finally {
                memory.pop()
            }
        }
    }

    override fun visitForIterateEachStmt(stmt: Stmt.ForIterateEach) {
        val iterableValue = visit(stmt.iterable)

        val iterableType = DataType.infer(this, iterableValue)

        val iterable =
            iterableType.iterable(this, iterableValue) ?: KBError.nonIterableType(iterableType, stmt.iterable.context)

        for (x in iterable) {
            try {
                memory.push("for iterate each pointers")

                val decl = stmt.decl.withValue(x)

                visit(decl)

                try {
                    visit(stmt.body)
                }
                catch (r: Redirect.Break) {
                    if (!(r.label.value.isEmpty() || r.label == stmt.label)) {
                        throw r
                    }

                    break
                }
                catch (r: Redirect.Next) {
                    if (!(r.label.value.isEmpty() || r.label == stmt.label)) {
                        throw r
                    }

                    continue
                }
            }
            finally {
                memory.pop()
            }
        }
    }

    override fun visitDataStmt(stmt: Stmt.Data) {
        if (!memory.newData(stmt)) {
            KBError.redeclaredData(stmt.name, stmt.context)
        }
    }

    override fun visitSubStmt(stmt: Stmt.Sub) {
        if (!memory.newSub(stmt)) {
            KBError.redeclaredSub(stmt.signature, stmt.context)
        }

        stmt.scope = memory.peek() ?: KBError.noScope(stmt.context)
    }

    override fun visitBreakStmt(stmt: Stmt.Break) {
        throw Redirect.Break(stmt.context, stmt.destination)
    }

    override fun visitNextStmt(stmt: Stmt.Next) {
        throw Redirect.Next(stmt.context, stmt.destination)
    }

    override fun visitReturnStmt(stmt: Stmt.Return) {
        throw Redirect.Return(stmt.context)
    }

    override fun visitYieldStmt(stmt: Stmt.Yield) {
        val value = visit(stmt.value)

        throw Redirect.Yield(stmt.context, value)
    }

    override fun visitTypeStmt(stmt: Stmt.Type) {
        if (!memory.newAlias(stmt.alias.value, stmt.type.value)) {
            KBError.redeclaredAlias(stmt.alias, stmt.alias.context)
        }
    }

    override fun visitBasicEnumStmt(stmt: Stmt.BasicEnum) {
        val entries = mutableListOf<EnumInstance.Entry>()

        for (entry in stmt.entries) {
            val (context, name, ordinal, value) = entry

            val ordinalResult = visit(ordinal)

            ordinalResult as? KBInt ?: TODO("INVALID BASIC ENUM ORDINAL '$ordinalResult' $context")

            val valueResult = visit(value)

            entries.add(EnumInstance.Entry(stmt.name.value, name.value, ordinalResult.value, valueResult))
        }

        memory.newEnum(stmt.name, EnumInstance(stmt.name.value, entries))
    }

    override fun visitDataEnumStmt(stmt: Stmt.DataEnum) {
        val type = DataType.resolve(this, stmt.type.value) as DataType.Data

        val entries = mutableListOf<EnumInstance.Entry>()

        for (entry in stmt.entries) {
            val (_, name, ordinal, value) = entry

            val ordinalResult = visit(ordinal)

            ordinalResult as? KBInt ?: TODO("INVALID DATA ENUM ORDINAL '$ordinalResult'")

            var instantiate = value

            if (instantiate.isInferred) {
                instantiate = instantiate.withTarget(type.name)
            }

            val valueResult = visit(instantiate)

            entries.add(EnumInstance.Entry(stmt.name.value, name.value, ordinalResult.value, valueResult))
        }

        memory.newEnum(stmt.name, EnumInstance(stmt.name.value, entries))
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression) {
        visit(stmt.expr)
    }

    override fun visitEmptyExpr(expr: Expr.Empty) =
        KBEmpty

    override fun visitValueExpr(expr: Expr.Value) =
        expr.value

    override fun visitNameExpr(expr: Expr.Name) =
        memory.getRef(expr)?.value ?: KBError.undeclaredVariable(expr, expr.context)

    override fun visitVariableExpr(expr: Expr.Variable) =
        KBEmpty//TODO memory.getRef(expr) ?: KBError.undeclaredVariable(expr, expr.context)

    override fun visitArrayExpr(expr: Expr.Array): KBV {
        val elements = mutableListOf<KBV>()

        for ((elementExpr, each) in expr.elements) {
            val element = visit(elementExpr)

            if (each) {
                when (element) {
                    is KBString -> elements.addAll(element.value.toCharArray().map(::KBChar))

                    is KBArray  -> elements.addAll(element.value)

                    else        -> TODO("CAN'T EACH")
                }
            }
            else {
                elements += element
            }
        }

        val type = DataType.infer(this, elements) as DataType.Array

        return KBArray(ArrayInstance(type.subType, elements))
    }

    override fun visitUnaryExpr(expr: Expr.Unary): KBV {
        return when (expr.op) {
            Expr.Unary.Operator.NEGATE -> when (val e = visit(expr.expr)) {
                is KBByte   -> KBByte((-e.value).toByte())

                is KBShort  -> KBShort((-e.value).toShort())

                is KBInt    -> KBInt(-e.value)

                is KBLong   -> KBLong(-e.value)

                is KBFloat  -> KBFloat(-e.value)

                is KBDouble -> KBDouble(-e.value)

                is KBString -> KBString(e.value.reversed())

                is KBArray  -> KBArray(ArrayInstance(e.value.type, e.value.reversed().toMutableList()))

                is KBData   -> e.value.invokeUnaryOperator(this, expr.op)

                else        -> KBError.invalidUnaryOperand(e, expr.op, expr.expr.context)
            }

            Expr.Unary.Operator.NOT    -> KBBool(visit(expr.expr).isEmptyValue())

            Expr.Unary.Operator.LENGTH -> when (val e = visit(expr.expr)) {
                is KBString -> KBInt(e.value.length)

                is KBArray  -> KBInt(e.value.size)

                is KBData   -> KBInt(e.value.deref().size)

                is KBEnum   -> KBInt(e.value.ordinal)

                else        -> KBInt(1)
            }

            Expr.Unary.Operator.STRING -> when (val e = visit(expr.expr)) {
                is KBEnum -> KBString(e.value.name)

                else      -> KBString(e.value.toString())
            }

            Expr.Unary.Operator.VALUE  -> when (val e = visit(expr.expr)) {
                is KBEnum -> e.value.value

                else      -> e
            }
        }
    }

    override fun visitBinaryExpr(expr: Expr.Binary): KBV {
        return when (expr.op) {
            Expr.Binary.Operator.OR            -> {
                val l = visit(expr.left)

                l as? KBBool ?: KBError.invalidLeftOperand(l, expr.op, expr.left.context)

                if (l.value) return l

                val r = visit(expr.right)

                r as? KBBool ?: KBError.invalidRightOperand(r, expr.op, expr.right.context)

                return r
            }

            Expr.Binary.Operator.AND           -> {
                val l = visit(expr.left)

                l as? KBBool ?: KBError.invalidLeftOperand(l, expr.op, expr.left.context)

                if (!l.value) return l

                val r = visit(expr.right)

                r as? KBBool ?: KBError.invalidRightOperand(r, expr.op, expr.right.context)

                return r
            }

            Expr.Binary.Operator.EQUAL         -> KBBool(
                when (val l = visit(expr.left)) {
                    is KBBool   -> when (val r = visit(expr.right)) {
                        is KBBool -> l.value == r.value
                        KBEmpty   -> l.value.isEmptyValue()
                        else      -> false
                    }

                    is KBByte   -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value == r.value
                        is KBShort  -> l.value.toShort() == r.value
                        is KBInt    -> l.value.toInt() == r.value
                        is KBLong   -> l.value.toLong() == r.value
                        is KBFloat  -> l.value.toFloat() == r.value
                        is KBDouble -> l.value.toDouble() == r.value
                        KBEmpty     -> l.value.isEmptyValue()
                        else        -> false
                    }

                    is KBShort  -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value == r.value.toShort()
                        is KBShort  -> l.value == r.value
                        is KBInt    -> l.value.toInt() == r.value
                        is KBLong   -> l.value.toLong() == r.value
                        is KBFloat  -> l.value.toFloat() == r.value
                        is KBDouble -> l.value.toDouble() == r.value
                        KBEmpty     -> l.value.isEmptyValue()
                        else        -> false
                    }

                    is KBInt    -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value == r.value.toInt()
                        is KBShort  -> l.value == r.value.toInt()
                        is KBInt    -> l.value == r.value
                        is KBLong   -> l.value.toLong() == r.value
                        is KBFloat  -> l.value.toFloat() == r.value
                        is KBDouble -> l.value.toDouble() == r.value
                        KBEmpty     -> l.value.isEmptyValue()
                        else        -> false
                    }

                    is KBLong   -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value == r.value.toLong()
                        is KBShort  -> l.value == r.value.toLong()
                        is KBInt    -> l.value == r.value.toLong()
                        is KBLong   -> l.value == r.value
                        is KBFloat  -> l.value.toFloat() == r.value
                        is KBDouble -> l.value.toDouble() == r.value
                        KBEmpty     -> l.value.isEmptyValue()
                        else        -> false
                    }

                    is KBFloat  -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value == r.value.toFloat()
                        is KBShort  -> l.value == r.value.toFloat()
                        is KBInt    -> l.value == r.value.toFloat()
                        is KBLong   -> l.value == r.value.toFloat()
                        is KBFloat  -> l.value == r.value
                        is KBDouble -> l.value.toDouble() == r.value
                        KBEmpty     -> l.value.isEmptyValue()
                        else        -> false
                    }

                    is KBDouble -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value == r.value.toDouble()
                        is KBShort  -> l.value == r.value.toDouble()
                        is KBInt    -> l.value == r.value.toDouble()
                        is KBLong   -> l.value == r.value.toDouble()
                        is KBFloat  -> l.value == r.value.toDouble()
                        is KBDouble -> l.value == r.value
                        KBEmpty     -> l.value.isEmptyValue()
                        else        -> false
                    }

                    is KBChar   -> when (val r = visit(expr.right)) {
                        is KBChar -> l.value == r.value
                        KBEmpty   -> l.value.isEmptyValue()
                        else      -> false
                    }

                    is KBString -> when (val r = visit(expr.right)) {
                        is KBString -> l.value == r.value
                        KBEmpty     -> l.value.isEmptyValue()
                        else        -> false
                    }

                    is KBArray  -> when (val r = visit(expr.right)) {
                        is KBArray -> l.value == r.value
                        KBEmpty    -> l.value.isEmpty()
                        else       -> false
                    }

                    is KBData   -> when (val r = visit(expr.right)) {
                        is KBData -> l.value == r.value
                        KBEmpty   -> l.value.isEmpty()
                        else      -> false
                    }

                    else        -> false
                }
            )

            Expr.Binary.Operator.NOT_EQUAL     -> KBBool(
                when (val l = visit(expr.left)) {
                    is KBBool   -> when (val r = visit(expr.right)) {
                        is KBBool -> l.value != r.value
                        KBEmpty   -> !l.value.isEmptyValue()
                        else      -> true
                    }

                    is KBByte   -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value != r.value
                        is KBShort  -> l.value.toShort() != r.value
                        is KBInt    -> l.value.toInt() != r.value
                        is KBLong   -> l.value.toLong() != r.value
                        is KBFloat  -> l.value.toFloat() != r.value
                        is KBDouble -> l.value.toDouble() != r.value
                        KBEmpty     -> !l.value.isEmptyValue()
                        else        -> true
                    }

                    is KBShort  -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value != r.value.toShort()
                        is KBShort  -> l.value != r.value
                        is KBInt    -> l.value.toInt() != r.value
                        is KBLong   -> l.value.toLong() != r.value
                        is KBFloat  -> l.value.toFloat() != r.value
                        is KBDouble -> l.value.toDouble() != r.value
                        KBEmpty     -> !l.value.isEmptyValue()
                        else        -> true
                    }

                    is KBInt    -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value != r.value.toInt()
                        is KBShort  -> l.value != r.value.toInt()
                        is KBInt    -> l.value != r.value
                        is KBLong   -> l.value.toLong() != r.value
                        is KBFloat  -> l.value.toFloat() != r.value
                        is KBDouble -> l.value.toDouble() != r.value
                        KBEmpty     -> !l.value.isEmptyValue()
                        else        -> true
                    }

                    is KBLong   -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value != r.value.toLong()
                        is KBShort  -> l.value != r.value.toLong()
                        is KBInt    -> l.value != r.value.toLong()
                        is KBLong   -> l.value != r.value
                        is KBFloat  -> l.value.toFloat() != r.value
                        is KBDouble -> l.value.toDouble() != r.value
                        KBEmpty     -> !l.value.isEmptyValue()
                        else        -> true
                    }

                    is KBFloat  -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value != r.value.toFloat()
                        is KBShort  -> l.value != r.value.toFloat()
                        is KBInt    -> l.value != r.value.toFloat()
                        is KBLong   -> l.value != r.value.toFloat()
                        is KBFloat  -> l.value != r.value
                        is KBDouble -> l.value.toDouble() != r.value
                        KBEmpty     -> !l.value.isEmptyValue()
                        else        -> true
                    }

                    is KBDouble -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value != r.value.toDouble()
                        is KBShort  -> l.value != r.value.toDouble()
                        is KBInt    -> l.value != r.value.toDouble()
                        is KBLong   -> l.value != r.value.toDouble()
                        is KBFloat  -> l.value != r.value.toDouble()
                        is KBDouble -> l.value != r.value
                        KBEmpty     -> !l.value.isEmptyValue()
                        else        -> true
                    }

                    is KBChar   -> when (val r = visit(expr.right)) {
                        is KBChar -> l.value != r.value
                        KBEmpty   -> !l.value.isEmptyValue()
                        else      -> true
                    }

                    is KBString -> when (val r = visit(expr.right)) {
                        is KBString -> l.value != r.value
                        KBEmpty     -> !l.value.isEmptyValue()
                        else        -> true
                    }

                    is KBArray  -> when (val r = visit(expr.right)) {
                        is KBArray -> l.value != r.value
                        KBEmpty    -> !l.value.isEmpty()
                        else       -> true
                    }

                    is KBData   -> when (val r = visit(expr.right)) {
                        is KBData -> l.value != r.value
                        KBEmpty   -> !l.value.isEmpty()
                        else      -> true
                    }

                    else        -> true
                }
            )

            Expr.Binary.Operator.LESS          -> KBBool(
                when (val l = visit(expr.left)) {
                    is KBByte   -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value < r.value
                        is KBShort  -> l.value < r.value
                        is KBInt    -> l.value < r.value
                        is KBLong   -> l.value < r.value
                        is KBFloat  -> l.value < r.value
                        is KBDouble -> l.value < r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBShort  -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value < r.value
                        is KBShort  -> l.value < r.value
                        is KBInt    -> l.value < r.value
                        is KBLong   -> l.value < r.value
                        is KBFloat  -> l.value < r.value
                        is KBDouble -> l.value < r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBInt    -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value < r.value
                        is KBShort  -> l.value < r.value
                        is KBInt    -> l.value < r.value
                        is KBLong   -> l.value < r.value
                        is KBFloat  -> l.value < r.value
                        is KBDouble -> l.value < r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBLong   -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value < r.value
                        is KBShort  -> l.value < r.value
                        is KBInt    -> l.value < r.value
                        is KBLong   -> l.value < r.value
                        is KBFloat  -> l.value < r.value
                        is KBDouble -> l.value < r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBFloat  -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value < r.value
                        is KBShort  -> l.value < r.value
                        is KBInt    -> l.value < r.value
                        is KBLong   -> l.value < r.value
                        is KBFloat  -> l.value < r.value
                        is KBDouble -> l.value < r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBDouble -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value < r.value
                        is KBShort  -> l.value < r.value
                        is KBInt    -> l.value < r.value
                        is KBLong   -> l.value < r.value
                        is KBFloat  -> l.value < r.value
                        is KBDouble -> l.value < r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBChar   -> when (val r = visit(expr.right)) {
                        is KBChar -> l.value < r.value
                        else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBString -> when (val r = visit(expr.right)) {
                        is KBString -> l.value < r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    else        -> KBError.invalidLeftOperand(l, expr.op, expr.left.context)
                }
            )

            Expr.Binary.Operator.LESS_EQUAL    -> KBBool(
                when (val l = visit(expr.left)) {
                    is KBByte   -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value <= r.value
                        is KBShort  -> l.value <= r.value
                        is KBInt    -> l.value <= r.value
                        is KBLong   -> l.value <= r.value
                        is KBFloat  -> l.value <= r.value
                        is KBDouble -> l.value <= r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBShort  -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value <= r.value
                        is KBShort  -> l.value <= r.value
                        is KBInt    -> l.value <= r.value
                        is KBLong   -> l.value <= r.value
                        is KBFloat  -> l.value <= r.value
                        is KBDouble -> l.value <= r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBInt    -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value <= r.value
                        is KBShort  -> l.value <= r.value
                        is KBInt    -> l.value <= r.value
                        is KBLong   -> l.value <= r.value
                        is KBFloat  -> l.value <= r.value
                        is KBDouble -> l.value <= r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBLong   -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value <= r.value
                        is KBShort  -> l.value <= r.value
                        is KBInt    -> l.value <= r.value
                        is KBLong   -> l.value <= r.value
                        is KBFloat  -> l.value <= r.value
                        is KBDouble -> l.value <= r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBFloat  -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value <= r.value
                        is KBShort  -> l.value <= r.value
                        is KBInt    -> l.value <= r.value
                        is KBLong   -> l.value <= r.value
                        is KBFloat  -> l.value <= r.value
                        is KBDouble -> l.value <= r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBDouble -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value <= r.value
                        is KBShort  -> l.value <= r.value
                        is KBInt    -> l.value <= r.value
                        is KBLong   -> l.value <= r.value
                        is KBFloat  -> l.value <= r.value
                        is KBDouble -> l.value <= r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBChar   -> when (val r = visit(expr.right)) {
                        is KBChar -> l.value <= r.value
                        else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBString -> when (val r = visit(expr.right)) {
                        is KBString -> l.value <= r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    else        -> KBError.invalidLeftOperand(l, expr.op, expr.left.context)
                }
            )

            Expr.Binary.Operator.GREATER       -> KBBool(
                when (val l = visit(expr.left)) {
                    is KBByte   -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value > r.value
                        is KBShort  -> l.value > r.value
                        is KBInt    -> l.value > r.value
                        is KBLong   -> l.value > r.value
                        is KBFloat  -> l.value > r.value
                        is KBDouble -> l.value > r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBShort  -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value > r.value
                        is KBShort  -> l.value > r.value
                        is KBInt    -> l.value > r.value
                        is KBLong   -> l.value > r.value
                        is KBFloat  -> l.value > r.value
                        is KBDouble -> l.value > r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBInt    -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value > r.value
                        is KBShort  -> l.value > r.value
                        is KBInt    -> l.value > r.value
                        is KBLong   -> l.value > r.value
                        is KBFloat  -> l.value > r.value
                        is KBDouble -> l.value > r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBLong   -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value > r.value
                        is KBShort  -> l.value > r.value
                        is KBInt    -> l.value > r.value
                        is KBLong   -> l.value > r.value
                        is KBFloat  -> l.value > r.value
                        is KBDouble -> l.value > r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBFloat  -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value > r.value
                        is KBShort  -> l.value > r.value
                        is KBInt    -> l.value > r.value
                        is KBLong   -> l.value > r.value
                        is KBFloat  -> l.value > r.value
                        is KBDouble -> l.value > r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBDouble -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value > r.value
                        is KBShort  -> l.value > r.value
                        is KBInt    -> l.value > r.value
                        is KBLong   -> l.value > r.value
                        is KBFloat  -> l.value > r.value
                        is KBDouble -> l.value > r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBChar   -> when (val r = visit(expr.right)) {
                        is KBChar -> l.value > r.value
                        else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBString -> when (val r = visit(expr.right)) {
                        is KBString -> l.value > r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    else        -> KBError.invalidLeftOperand(l, expr.op, expr.left.context)
                }
            )

            Expr.Binary.Operator.GREATER_EQUAL -> KBBool(
                when (val l = visit(expr.left)) {
                    is KBByte   -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value >= r.value
                        is KBShort  -> l.value >= r.value
                        is KBInt    -> l.value >= r.value
                        is KBLong   -> l.value >= r.value
                        is KBFloat  -> l.value >= r.value
                        is KBDouble -> l.value >= r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBShort  -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value >= r.value
                        is KBShort  -> l.value >= r.value
                        is KBInt    -> l.value >= r.value
                        is KBLong   -> l.value >= r.value
                        is KBFloat  -> l.value >= r.value
                        is KBDouble -> l.value >= r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBInt    -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value >= r.value
                        is KBShort  -> l.value >= r.value
                        is KBInt    -> l.value >= r.value
                        is KBLong   -> l.value >= r.value
                        is KBFloat  -> l.value >= r.value
                        is KBDouble -> l.value >= r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBLong   -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value >= r.value
                        is KBShort  -> l.value >= r.value
                        is KBInt    -> l.value >= r.value
                        is KBLong   -> l.value >= r.value
                        is KBFloat  -> l.value >= r.value
                        is KBDouble -> l.value >= r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBFloat  -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value >= r.value
                        is KBShort  -> l.value >= r.value
                        is KBInt    -> l.value >= r.value
                        is KBLong   -> l.value >= r.value
                        is KBFloat  -> l.value >= r.value
                        is KBDouble -> l.value >= r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBDouble -> when (val r = visit(expr.right)) {
                        is KBByte   -> l.value >= r.value
                        is KBShort  -> l.value >= r.value
                        is KBInt    -> l.value >= r.value
                        is KBLong   -> l.value >= r.value
                        is KBFloat  -> l.value >= r.value
                        is KBDouble -> l.value >= r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBChar   -> when (val r = visit(expr.right)) {
                        is KBChar -> l.value >= r.value
                        else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    is KBString -> when (val r = visit(expr.right)) {
                        is KBString -> l.value >= r.value
                        else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                    }

                    else        -> KBError.invalidLeftOperand(l, expr.op, expr.left.context)
                }
            )

            Expr.Binary.Operator.CONCAT        -> {
                val l = visit(expr.left).value
                val r = visit(expr.right).value

                KBString("$l$r")
            }

            Expr.Binary.Operator.ADD           -> when (val l = visit(expr.left)) {
                is KBByte   -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBByte((l.value + r.value).toByte())
                    is KBShort  -> KBShort((l.value + r.value).toShort())
                    is KBInt    -> KBInt(l.value + r.value)
                    is KBLong   -> KBLong(l.value + r.value)
                    is KBFloat  -> KBFloat(l.value + r.value)
                    is KBDouble -> KBDouble(l.value + r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBShort  -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBShort((l.value + r.value).toShort())
                    is KBShort  -> KBShort((l.value + r.value).toShort())
                    is KBInt    -> KBInt(l.value + r.value)
                    is KBLong   -> KBLong(l.value + r.value)
                    is KBFloat  -> KBFloat(l.value + r.value)
                    is KBDouble -> KBDouble(l.value + r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBInt    -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBInt(l.value + r.value)
                    is KBShort  -> KBInt(l.value + r.value)
                    is KBInt    -> KBInt(l.value + r.value)
                    is KBLong   -> KBLong(l.value + r.value)
                    is KBFloat  -> KBFloat(l.value + r.value)
                    is KBDouble -> KBDouble(l.value + r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBLong   -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBLong(l.value + r.value)
                    is KBShort  -> KBLong(l.value + r.value)
                    is KBInt    -> KBLong(l.value + r.value)
                    is KBLong   -> KBLong(l.value + r.value)
                    is KBFloat  -> KBFloat(l.value + r.value)
                    is KBDouble -> KBDouble(l.value + r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBFloat  -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBFloat(l.value + r.value)
                    is KBShort  -> KBFloat(l.value + r.value)
                    is KBInt    -> KBFloat(l.value + r.value)
                    is KBLong   -> KBFloat(l.value + r.value)
                    is KBFloat  -> KBFloat(l.value + r.value)
                    is KBDouble -> KBDouble(l.value + r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBDouble -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBDouble(l.value + r.value)
                    is KBShort  -> KBDouble(l.value + r.value)
                    is KBInt    -> KBDouble(l.value + r.value)
                    is KBLong   -> KBDouble(l.value + r.value)
                    is KBFloat  -> KBDouble(l.value + r.value)
                    is KBDouble -> KBDouble(l.value + r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBChar   -> when (val r = visit(expr.right)) {
                    is KBInt -> KBChar((l.value.code + r.value).toChar())
                    else     -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBData   -> l.value.invokeBinaryOperator(this, expr.op, visit(expr.right))

                else        -> KBError.invalidLeftOperand(l, expr.op, expr.left.context)
            }

            Expr.Binary.Operator.SUBTRACT      -> when (val l = visit(expr.left)) {
                is KBByte   -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBByte((l.value - r.value).toByte())
                    is KBShort  -> KBShort((l.value - r.value).toShort())
                    is KBInt    -> KBInt(l.value - r.value)
                    is KBLong   -> KBLong(l.value - r.value)
                    is KBFloat  -> KBFloat(l.value - r.value)
                    is KBDouble -> KBDouble(l.value - r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBShort  -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBShort((l.value - r.value).toShort())
                    is KBShort  -> KBShort((l.value - r.value).toShort())
                    is KBInt    -> KBInt(l.value - r.value)
                    is KBLong   -> KBLong(l.value - r.value)
                    is KBFloat  -> KBFloat(l.value - r.value)
                    is KBDouble -> KBDouble(l.value - r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBInt    -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBInt(l.value - r.value)
                    is KBShort  -> KBInt(l.value - r.value)
                    is KBInt    -> KBInt(l.value - r.value)
                    is KBLong   -> KBLong(l.value - r.value)
                    is KBFloat  -> KBFloat(l.value - r.value)
                    is KBDouble -> KBDouble(l.value - r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBLong   -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBLong(l.value - r.value)
                    is KBShort  -> KBLong(l.value - r.value)
                    is KBInt    -> KBLong(l.value - r.value)
                    is KBLong   -> KBLong(l.value - r.value)
                    is KBFloat  -> KBFloat(l.value - r.value)
                    is KBDouble -> KBDouble(l.value - r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBFloat  -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBFloat(l.value - r.value)
                    is KBShort  -> KBFloat(l.value - r.value)
                    is KBInt    -> KBFloat(l.value - r.value)
                    is KBLong   -> KBFloat(l.value - r.value)
                    is KBFloat  -> KBFloat(l.value - r.value)
                    is KBDouble -> KBDouble(l.value - r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBDouble -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBDouble(l.value - r.value)
                    is KBShort  -> KBDouble(l.value - r.value)
                    is KBInt    -> KBDouble(l.value - r.value)
                    is KBLong   -> KBDouble(l.value - r.value)
                    is KBFloat  -> KBDouble(l.value - r.value)
                    is KBDouble -> KBDouble(l.value - r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBChar   -> when (val r = visit(expr.right)) {
                    is KBInt -> KBChar((l.value.code - r.value).toChar())
                    else     -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBData   -> l.value.invokeBinaryOperator(this, expr.op, visit(expr.right))

                else        -> KBError.invalidLeftOperand(l, expr.op, expr.left.context)
            }

            Expr.Binary.Operator.MULTIPLY      -> when (val l = visit(expr.left)) {
                is KBByte   -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBByte((l.value * r.value).toByte())
                    is KBShort  -> KBShort((l.value * r.value).toShort())
                    is KBInt    -> KBInt(l.value * r.value)
                    is KBLong   -> KBLong(l.value * r.value)
                    is KBFloat  -> KBFloat(l.value * r.value)
                    is KBDouble -> KBDouble(l.value * r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBShort  -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBShort((l.value * r.value).toShort())
                    is KBShort  -> KBShort((l.value * r.value).toShort())
                    is KBInt    -> KBInt(l.value * r.value)
                    is KBLong   -> KBLong(l.value * r.value)
                    is KBFloat  -> KBFloat(l.value * r.value)
                    is KBDouble -> KBDouble(l.value * r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBInt    -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBInt(l.value * r.value)
                    is KBShort  -> KBInt(l.value * r.value)
                    is KBInt    -> KBInt(l.value * r.value)
                    is KBLong   -> KBLong(l.value * r.value)
                    is KBFloat  -> KBFloat(l.value * r.value)
                    is KBDouble -> KBDouble(l.value * r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBLong   -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBLong(l.value * r.value)
                    is KBShort  -> KBLong(l.value * r.value)
                    is KBInt    -> KBLong(l.value * r.value)
                    is KBLong   -> KBLong(l.value * r.value)
                    is KBFloat  -> KBFloat(l.value * r.value)
                    is KBDouble -> KBDouble(l.value * r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBFloat  -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBFloat(l.value * r.value)
                    is KBShort  -> KBFloat(l.value * r.value)
                    is KBInt    -> KBFloat(l.value * r.value)
                    is KBLong   -> KBFloat(l.value * r.value)
                    is KBFloat  -> KBFloat(l.value * r.value)
                    is KBDouble -> KBDouble(l.value * r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBDouble -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBDouble(l.value * r.value)
                    is KBShort  -> KBDouble(l.value * r.value)
                    is KBInt    -> KBDouble(l.value * r.value)
                    is KBLong   -> KBDouble(l.value * r.value)
                    is KBFloat  -> KBDouble(l.value * r.value)
                    is KBDouble -> KBDouble(l.value * r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBChar   -> when (val r = visit(expr.right)) {
                    is KBInt -> KBChar((l.value.code * r.value).toChar())
                    else     -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBData   -> l.value.invokeBinaryOperator(this, expr.op, visit(expr.right))

                else        -> KBError.invalidLeftOperand(l, expr.op, expr.left.context)
            }

            Expr.Binary.Operator.DIVIDE        -> when (val l = visit(expr.left)) {
                is KBByte   -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBByte((l.value / r.value).toByte())
                    is KBShort  -> KBShort((l.value / r.value).toShort())
                    is KBInt    -> KBInt(l.value / r.value)
                    is KBLong   -> KBLong(l.value / r.value)
                    is KBFloat  -> KBFloat(l.value / r.value)
                    is KBDouble -> KBDouble(l.value / r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBShort  -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBShort((l.value / r.value).toShort())
                    is KBShort  -> KBShort((l.value / r.value).toShort())
                    is KBInt    -> KBInt(l.value / r.value)
                    is KBLong   -> KBLong(l.value / r.value)
                    is KBFloat  -> KBFloat(l.value / r.value)
                    is KBDouble -> KBDouble(l.value / r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBInt    -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBInt(l.value / r.value)
                    is KBShort  -> KBInt(l.value / r.value)
                    is KBInt    -> KBInt(l.value / r.value)
                    is KBLong   -> KBLong(l.value / r.value)
                    is KBFloat  -> KBFloat(l.value / r.value)
                    is KBDouble -> KBDouble(l.value / r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBLong   -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBLong(l.value / r.value)
                    is KBShort  -> KBLong(l.value / r.value)
                    is KBInt    -> KBLong(l.value / r.value)
                    is KBLong   -> KBLong(l.value / r.value)
                    is KBFloat  -> KBFloat(l.value / r.value)
                    is KBDouble -> KBDouble(l.value / r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBFloat  -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBFloat(l.value / r.value)
                    is KBShort  -> KBFloat(l.value / r.value)
                    is KBInt    -> KBFloat(l.value / r.value)
                    is KBLong   -> KBFloat(l.value / r.value)
                    is KBFloat  -> KBFloat(l.value / r.value)
                    is KBDouble -> KBDouble(l.value / r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBDouble -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBDouble(l.value / r.value)
                    is KBShort  -> KBDouble(l.value / r.value)
                    is KBInt    -> KBDouble(l.value / r.value)
                    is KBLong   -> KBDouble(l.value / r.value)
                    is KBFloat  -> KBDouble(l.value / r.value)
                    is KBDouble -> KBDouble(l.value / r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBChar   -> when (val r = visit(expr.right)) {
                    is KBInt -> KBChar((l.value.code / r.value).toChar())
                    else     -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBData   -> l.value.invokeBinaryOperator(this, expr.op, visit(expr.right))

                else        -> KBError.invalidLeftOperand(l, expr.op, expr.left.context)
            }

            Expr.Binary.Operator.MODULUS       -> when (val l = visit(expr.left)) {
                is KBByte   -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBByte((l.value % r.value).toByte())
                    is KBShort  -> KBShort((l.value % r.value).toShort())
                    is KBInt    -> KBInt(l.value % r.value)
                    is KBLong   -> KBLong(l.value % r.value)
                    is KBFloat  -> KBFloat(l.value % r.value)
                    is KBDouble -> KBDouble(l.value % r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBShort  -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBShort((l.value % r.value).toShort())
                    is KBShort  -> KBShort((l.value % r.value).toShort())
                    is KBInt    -> KBInt(l.value % r.value)
                    is KBLong   -> KBLong(l.value % r.value)
                    is KBFloat  -> KBFloat(l.value % r.value)
                    is KBDouble -> KBDouble(l.value % r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBInt    -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBInt(l.value % r.value)
                    is KBShort  -> KBInt(l.value % r.value)
                    is KBInt    -> KBInt(l.value % r.value)
                    is KBLong   -> KBLong(l.value % r.value)
                    is KBFloat  -> KBFloat(l.value % r.value)
                    is KBDouble -> KBDouble(l.value % r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBLong   -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBLong(l.value % r.value)
                    is KBShort  -> KBLong(l.value % r.value)
                    is KBInt    -> KBLong(l.value % r.value)
                    is KBLong   -> KBLong(l.value % r.value)
                    is KBFloat  -> KBFloat(l.value % r.value)
                    is KBDouble -> KBDouble(l.value % r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBFloat  -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBFloat(l.value % r.value)
                    is KBShort  -> KBFloat(l.value % r.value)
                    is KBInt    -> KBFloat(l.value % r.value)
                    is KBLong   -> KBFloat(l.value % r.value)
                    is KBFloat  -> KBFloat(l.value % r.value)
                    is KBDouble -> KBDouble(l.value % r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBDouble -> when (val r = visit(expr.right)) {
                    is KBByte   -> KBDouble(l.value % r.value)
                    is KBShort  -> KBDouble(l.value % r.value)
                    is KBInt    -> KBDouble(l.value % r.value)
                    is KBLong   -> KBDouble(l.value % r.value)
                    is KBFloat  -> KBDouble(l.value % r.value)
                    is KBDouble -> KBDouble(l.value % r.value)
                    else        -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBChar   -> when (val r = visit(expr.right)) {
                    is KBInt -> KBChar((l.value.code % r.value).toChar())
                    else     -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is KBData   -> l.value.invokeBinaryOperator(this, expr.op, visit(expr.right))

                else        -> KBError.invalidLeftOperand(l, expr.op, expr.left.context)
            }
        }
    }

    override fun visitAssignExpr(expr: Expr.Assign): KBV {
        val ref = memory.getRef(expr.name) ?: TODO()

        val type = ref.type

        var value = expr.value

        if (type is DataType.Data && value is Expr.Instantiate && value.isInferred) {
            value = value.withTarget(type.name)
        }

        var v = visit(value)

        if (v === KBEmpty) {
            v = type.default(this) ?: KBError.noDefaultValue(type, expr.name.context)
        }

        v = type.coerce(v) ?: v

        return when (ref.put(this, v)) {
            true  -> v

            false -> {
                if (type is DataType.Array && type.mismatchedSize(this, v)) {
                    KBError.mismatchedArraySize(expr.value.context)
                }

                KBError.mismatchedType(v, type, expr.context)
            }

            null  -> KBError.reassignedConstant(expr.context)
        }
    }

    override fun visitTypeCheckExpr(expr: Expr.TypeCheck): KBBool {
        val value = visit(expr.value)

        val type = DataType.resolve(this, expr.type.value)

        return KBBool(type.filter(this, value) != null && !expr.invert)
    }

    override fun visitTypeCastExpr(expr: Expr.TypeCast): KBV {
        val value = visit(expr.value)

        val type = DataType.resolve(this, expr.type.value)

        return type.cast(this, value) ?: KBError.invalidCast(value, type, expr.context)
    }

    override fun visitGetIndexExpr(expr: Expr.GetIndex): KBV {
        return when (val target = visit(expr.target)) {
            is KBString -> when (val index = visit(expr.index)) {
                is KBInt -> KBChar(target.value[index.value])

                else     -> KBError.invalidIndex(
                    DataType.infer(this, target),
                    DataType.infer(this, index),
                    expr.index.context
                )
            }

            is KBArray  -> when (val index = DataType.Primitive.INT.coerce(visit(expr.index))) {
                is KBInt -> target.value[index.value]

                else     -> KBError.invalidIndex(
                    DataType.infer(this, target),
                    DataType.infer(this, index),
                    expr.index.context
                )
            }

            is KBData   -> when (val index = visit(expr.index)) {
                is KBChar   -> target.value[index.value.toString()]?.value ?: KBError.noMember(
                    target.value.name,
                    index.toString(),
                    expr.index.context
                )

                is KBString -> target.value[index.value]?.value ?: KBError.noMember(
                    target.value.name,
                    index.value,
                    expr.index.context
                )

                else        -> KBError.invalidIndex(
                    DataType.infer(this, target),
                    DataType.infer(this, index),
                    expr.index.context
                )
            }

            else        -> KBError.nonIndexedType(DataType.infer(this, target), expr.target.context)
        }
    }

    override fun visitSetIndexExpr(expr: Expr.SetIndex): KBV {
        return when (val target = visit(expr.target)) {
            is KBArray -> when (val index = DataType.Primitive.INT.coerce(visit(expr.index))) {
                is KBInt -> {
                    val type = target.value.type
                    var subExpr = expr.expr

                    if (type is DataType.Data && subExpr is Expr.Instantiate && subExpr.isInferred) {
                        subExpr = subExpr.withTarget(type.name)
                    }

                    val result = visit(subExpr)
                    val value = type.coerce(result) ?: KBError.mismatchedType(result, type, subExpr.context)

                    if (target.value.type.filter(this, value) == null) {
                        KBError.mismatchedType(value, target.value.type, expr.context)
                    }

                    target.value[index.value] = value

                    value
                }

                else   -> KBError.invalidIndex(
                    DataType.infer(this, target),
                    DataType.infer(this, index),
                    expr.index.context
                )
            }

            is KBData  -> when (val index = visit(expr.index)) {
                is KBChar   -> {
                    val ref =
                        target.value[index.value.toString()] ?: KBError.noMember(target.value.name, index.toString(), expr.index.context)

                    val type = ref.type
                    var subExpr = expr.expr

                    if (type is DataType.Data && subExpr is Expr.Instantiate && subExpr.isInferred) {
                        subExpr = subExpr.withTarget(type.name)
                    }

                    val result = visit(subExpr)
                    val value = type.coerce(result) ?: KBError.mismatchedType(result, type, subExpr.context)

                    when (ref.put(this, value)) {
                        true  -> value

                        false -> KBError.mismatchedType(value, ref.type, expr.context)

                        null  -> KBError.reassignedConstant(expr.index.context)
                    }
                }

                is KBString -> {
                    val ref = target.value[index.value] ?: KBError.noMember(target.value.name, index.value, expr.index.context)

                    val type = ref.type
                    var subExpr = expr.expr

                    if (type is DataType.Data && subExpr is Expr.Instantiate && subExpr.isInferred) {
                        subExpr = subExpr.withTarget(type.name)
                    }

                    val result = visit(subExpr)
                    val value = type.coerce(result) ?: KBError.mismatchedType(result, type, subExpr.context)

                    when (ref.put(this, value)) {
                        true  -> value

                        false -> KBError.mismatchedType(value, ref.type, expr.context)

                        null  -> KBError.reassignedConstant(expr.index.context)
                    }
                }

                else      -> KBError.invalidIndex(
                    DataType.infer(this, target),
                    DataType.infer(this, index),
                    expr.index.context
                )
            }

            else             -> KBError.nonIndexedType(DataType.infer(this, target), expr.target.context)
        }
    }

    override fun visitGetMemberExpr(expr: Expr.GetMember): KBV {
        val target = visit(expr.target)

        if (target is KBData) {
            return target.value[expr.member]?.value ?: KBError.noMember(
                target.value.name,
                expr.member.value,
                expr.context
            )
        }

        KBError.nonAccessedType(DataType.infer(this, target), expr.context)
    }

    override fun visitSetMemberExpr(expr: Expr.SetMember): KBNone {
        val target = visit(expr.target)

        if (target is KBData) {
            target.value[expr.member]?.put(this, visit(expr.expr))

            return KBNone
        }

        KBError.nonAccessedType(DataType.infer(this, target), expr.context)
    }

    override fun visitGetEntryExpr(expr: Expr.GetEntry): KBV {
        val enum = memory.getEnum(expr.target) ?: TODO("ENUM '${expr.target}' DOES NOT EXIST ${expr.context}")

        if (expr.member.value == "*") {
            return KBArray(
                ArrayInstance(
                    DataType.Enum(Expr.Name(Context.none, enum.name)),
                    enum.entries.map { KBEnum(it) }.toMutableList()
                )
            )
        }

        return KBEnum(enum[expr.member])
    }

    override fun visitInvokeExpr(expr: Expr.Invoke): KBV {
        val (mode, _, value) = performInvoke(expr)

        return when (mode) {
            KBInvoke.Mode.SUCCESS         -> value

            KBInvoke.Mode.FAIL_UNDECLARED -> KBError.undeclaredSub(expr.name, expr.name.context)

            KBInvoke.Mode.FAIL_POSITIONS  -> KBError.unresolvedPositions(expr.name, expr.context)

            KBInvoke.Mode.FAIL_TYPES      -> KBError.unresolvedTypes(expr.name, expr.context)
        }
    }

    private fun performInvoke(expr: Expr.Invoke): KBInvoke {
        val subs = memory.getSubs(expr.name) ?: return KBInvoke(KBInvoke.Mode.FAIL_UNDECLARED)

        val args = expr.args

        val positionValid = mutableListOf<Pair<Stmt.Sub, List<Stmt.Decl>>>()

        for (sub in subs) {
            val resolved = resolvePosition(sub.params, args) ?: continue

            positionValid += sub to resolved
        }

        if (positionValid.isEmpty()) {
            return KBInvoke(KBInvoke.Mode.FAIL_POSITIONS)
        }

        val typeValid = mutableListOf<Pair<Stmt.Sub, List<Stmt.Decl>>>()

        for ((sub, decls) in positionValid) {
            val resolved = resolveType(decls) ?: continue

            typeValid += sub to resolved
        }

        if (typeValid.isEmpty()) {
            return KBInvoke(KBInvoke.Mode.FAIL_TYPES)
        }

        val (sub, decls) = if (typeValid.size == 1)
            typeValid[0]
        else
            typeValid.minByOrNull { (_, decls) -> decls.size }!!

        val type = DataType.resolve(this, sub.definition.type.value)

        val scope = Memory.Scope(sub.definition.name.value, sub.scope)

        val ref: Memory.Scope.Reference

        try {
            memory.push(scope)

            for (decl in decls) {
                visit(decl)
            }

            memory.newRef(
                false,
                sub.definition.name,
                type,
                type.default(this) ?: KBNone//KBError.noDefaultValue(sub.type.value, sub.name.location)
            )

            ref = memory.getRef(sub.definition.name)!!

            if (sub.isLinked) {
                val subArgs = mutableListOf<KBV>()

                for (name in decls.map { it.definition.name }) {
                    subArgs += memory.getRef(name)!!.value
                }

                val builtin = library[sub] ?: KBError.noBuiltin(sub.definition.name, sub.context)

                val builtinValue =
                    builtin(this, subArgs) ?: KBError.mismatchedBuiltinType(sub.definition.name, type, sub.context)

                val value = type.filter(this, builtinValue)
                    ?: KBError.mismatchedReturnType(sub.definition.name, type, Context.none)

                return KBInvoke(KBInvoke.Mode.SUCCESS, type, value)
            }

            try {
                visit(sub.body)
            }
            catch (r: Redirect.Yield) {
                val value = type.filter(this, r.value)
                    ?: KBError.mismatchedReturnType(sub.definition.name, type, r.origin)

                return KBInvoke(KBInvoke.Mode.SUCCESS, type, value)
            }
            catch (r: Redirect.Return) {
                return KBInvoke(KBInvoke.Mode.SUCCESS, type, ref.value)
            }
        }
        finally {
            memory.pop()
        }

        val value = type.filter(this, ref.value) ?: KBError.noYield(sub.definition.name, expr.name.context)

        return KBInvoke(KBInvoke.Mode.SUCCESS, type, value)
    }

    private fun resolvePosition(params: List<Stmt.Decl>, args: List<Expr.Invoke.Argument>): List<Stmt.Decl>? {
        val isVararg = params.isNotEmpty() && params.last().isVararg

        if (!isVararg && args.size > params.size) {
            return null
        }

        val firstEach = args.indexOfFirst { it.each }

        if (isVararg && firstEach in 0 until params.lastIndex) {
            return null
        }

        val exprs = MutableList(params.size) { i -> params[i].expr }

        var p = 0

        for (i in exprs.indices) {
            if (p !in args.indices) continue

            if (params[i].isVararg) continue

            if (args[p].expr !is Expr.Empty) {
                exprs[i] = args[p].expr
            }

            p++
        }

        if (p in args.indices) {
            val varargs = mutableListOf<Expr.Array.Element>()

            while (p < args.size) {
                val (each, expr) = args[p++]

                varargs += Expr.Array.Element(expr, each)
            }

            exprs[exprs.lastIndex] = Expr.Array(varargs[0].expr.context, varargs)
        }

        if (isVararg && exprs.last() is Expr.Empty) {
            exprs[exprs.lastIndex] = Expr.Array(Context.none, emptyList())
        }

        return if (exprs.all { it !is Expr.Empty })
            params.mapIndexed { i, decl -> decl.withExpr(exprs[i]) }
        else
            null
    }

    private fun resolveType(params: List<Stmt.Decl>): List<Stmt.Decl>? {
        val exprs = params.map { it.expr }.toMutableList()

        for (i in params.indices) {
            val decl = params[i]

            val type = DataType.resolve(this, decl.definition.type.value)
            val value = visit(decl.expr)

            val finalValue = type.filter(this, type.coerce(value) ?: value) ?: return null

            exprs[i] = finalValue.toExpr()
        }

        return params.mapIndexed { i, decl -> decl.withExpr(exprs[i]) }
    }

    fun invoke(name: String, vararg arguments: Any): KBInvoke? {
        val invoke =
            Expr.Invoke(Context.none, name.toName(), arguments.map { Expr.Invoke.Argument(false, it.toExpr()) })

        val result = performInvoke(invoke)

        return when (result.mode) {
            KBInvoke.Mode.SUCCESS -> result

            else                  -> null
        }
    }

    fun getString(x: Any) =
        (invoke("getstring", x) ?: x.toString()) as? String ?: KBError.mismatchedReturnType(
            "getstring".toName(),
            DataType.Primitive.STRING,
            Context.none
        )

    fun instantiate(name: String, vararg elements: Any): KBData {
        val instantiate =
            Expr.Instantiate(Context.none, name.lowercase().toName(), elements.map { it.toExpr() }.toList())

        return visit(instantiate) as KBData
    }

    override fun visitInstantiateExpr(expr: Expr.Instantiate): KBData {
        if (expr.isInferred) {
            KBError.emptyInstantiationTarget(expr.context)
        }

        val data = memory.getData(expr.target) ?: KBError.undeclaredData(expr.target, expr.target.context)

        val scope = Memory.Scope(data.name.value, memory.peek())

        try {
            memory.push(scope)

            for (i in data.decls.indices) {
                var decl = data.decls[i]

                if (i in expr.elements.indices) {
                    var element = expr.elements[i]

                    val declType = DataType.resolve(this, decl.definition.type.value)

                    if (declType is DataType.Data && element is Expr.Instantiate && element.isInferred) {
                        element = element.withTarget(declType.name)
                    }

                    decl = decl.withExpr(element)
                }

                visit(decl)
            }
        }
        finally {
            memory.pop()
        }

        return KBData(DataInstance(data.name, scope))
    }
}