package kakkoiichris.kb.runtime

import kakkoiichris.kb.lexer.Context
import kakkoiichris.kb.parser.Expr
import kakkoiichris.kb.parser.Stmt
import kakkoiichris.kb.parser.toExpr
import kakkoiichris.kb.parser.toName
import kakkoiichris.kb.util.KBError
import kakkoiichris.kb.util.Source

class Runtime(private val stmts: List<Stmt>) : Stmt.Visitor<Unit>, Expr.Visitor<KBValue<*>> {
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
        var type = visit(stmt.definition.type) as DataType

        if (expr is Expr.Instantiate && expr.isInferred && type is DataType.Data) {
            expr = expr.withTarget(type.name)
        }

        var value = visit(expr)

        if (value === Unit) {
            KBError.assignedNone(stmt.expr.context)
        }

        if (type === DataType.Inferred) {
            if (value is InvokeResult) {
                type = value.type
                value = value.value
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

        val value = visit(stmt.expr).fromRef().unwrap()

        if (value === Unit) {
            KBError.assignedNone(stmt.expr.context)
        }

        for ((i, pair) in stmt.definitions.withIndex()) {
            var subValue = when (value) {
                is String        -> value[i]

                is ArrayInstance -> value[i]

                is DataInstance  -> value.deref()[i]

                else             -> KBError.nonPartitionedType(DataType.infer(this, value), stmt.expr.context)
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
        val testValue = visit(stmt.test).fromRef().unwrap()

        testValue as? Boolean ?: KBError.invalidTestExpression(testValue, stmt.test.context)

        if (testValue) {
            visit(stmt.body)

            return
        }

        visit(stmt.`else`)
    }

    override fun visitSwitchStmt(stmt: Stmt.Switch) {
        val subject = visit(stmt.subject).fromRef().unwrap()

        for (case in stmt.cases) {
            try {
                when (case) {
                    is Stmt.Switch.Case.Values -> {
                        for (test in case.tests) {
                            if (subject == visit(test).unwrap()) {
                                visit(case.block)

                                return
                            }
                        }
                    }

                    is Stmt.Switch.Case.Type   -> {
                        val type = visit(case.type) as DataType

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
            val testValue = visit(stmt.test).fromRef().unwrap()

            testValue as? Boolean ?: KBError.invalidTestExpression(testValue, stmt.test.context)

            if (!testValue) break

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

            val testValue = visit(stmt.test).fromRef().unwrap()

            testValue as? Boolean ?: KBError.invalidTestExpression(testValue, stmt.test.context)

            if (testValue) break
        }
    }

    override fun visitForCounterStmt(stmt: Stmt.ForCounter) {
        try {
            memory.push("for pointer")

            visit(stmt.decl)

            val pointer = stmt.decl.definition.name

            val test = Expr.Binary(stmt.to.context, Expr.Binary.Operator.LESS, pointer, stmt.to)

            val increment =
                Expr.Binary(
                    stmt.step.context,
                    Expr.Binary.Operator.ASSIGN,
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
            while (visit(test).fromRef().unwrap() as Boolean)
        }
        finally {
            memory.pop()
        }
    }

    override fun visitForIterateStmt(stmt: Stmt.ForIterate) {
        val iterableValue = visit(stmt.iterable).fromRef().unwrap()

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
        val iterableValue = visit(stmt.iterable).fromRef().unwrap()

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
        val value = visit(stmt.value).fromRef().unwrap()

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

            ordinalResult as? Int ?: TODO("INVALID BASIC ENUM ORDINAL '$ordinalResult' $context")

            val valueResult = visit(value).fromRef().unwrap()

            entries.add(EnumInstance.Entry(stmt.name.value, name.value, ordinalResult, valueResult))
        }

        memory.newEnum(stmt.name, EnumInstance(stmt.name.value, entries))
    }

    override fun visitDataEnumStmt(stmt: Stmt.DataEnum) {
        val type = visit(stmt.type) as DataType.Data

        val entries = mutableListOf<EnumInstance.Entry>()

        for (entry in stmt.entries) {
            val (_, name, ordinal, value) = entry

            val ordinalResult = visit(ordinal)

            ordinalResult as? Int ?: TODO("INVALID DATA ENUM ORDINAL '$ordinalResult'")

            var instantiate = value

            if (instantiate.isInferred) {
                instantiate = instantiate.withTarget(type.name)
            }

            val valueResult = visit(instantiate).fromRef()

            entries.add(EnumInstance.Entry(stmt.name.value, name.value, ordinalResult, valueResult))
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

    override fun visitTypeExpr(expr: Expr.Type) =
        DataType.resolve(this, expr.value)

    override fun visitArrayExpr(expr: Expr.Array): KBValue<*> {
        val elements = mutableListOf<Any>()

        for (elementNode in expr.elements) {
            val element = visit(elementNode).fromRef().unwrap()

            if (element is Expr.Each) {
                when (val value = visit(element.expr).fromRef().unwrap()) {
                    is String        -> elements.addAll(listOf(value.toCharArray()))

                    is ArrayInstance -> elements.addAll(value)

                    else             -> TODO("CAN'T EACH")
                }
            }
            else {
                elements += element
            }
        }

        val type = DataType.infer(this, elements) as DataType.Array

        return KBArray(ArrayInstance(type.subType, elements))
    }

    override fun visitUnaryExpr(expr: Expr.Unary): KBValue<*> {
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

                else                  -> KBString(e.value.toString())
            }

            Expr.Unary.Operator.VALUE  -> when (val e = visit(expr.expr)) {
                is EnumInstance.Entry -> e.value

                else                  -> e
            }
        }
    }

    override fun visitBinaryExpr(expr: Expr.Binary): Any {
        return when (expr.op) {
            Expr.Binary.Operator.ASSIGN        -> when (val l = visit(expr.left)) {
                is Memory.Scope.Reference -> {
                    val type = l.type

                    var right = expr.right

                    if (type is DataType.Data && right is Expr.Instantiate && right.isInferred) {
                        right = right.withTarget(type.name)
                    }

                    var r = visit(right).fromRef()

                    if (r === KBEmpty) {
                        r = type.default(this) ?: KBError.noDefaultValue(type, expr.left.context)
                    }

                    r = type.coerce(r) ?: r

                    when (l.put(this, r)) {
                        true  -> r

                        false -> {
                            if (type is DataType.Array && type.mismatchedSize(this, r)) {
                                KBError.mismatchedArraySize(expr.right.context)
                            }

                            KBError.mismatchedType(r, type, expr.context)
                        }

                        null  -> KBError.reassignedConstant(expr.context)
                    }
                }

                else                      -> KBError.assignedToValue(expr.left.context)
            }

            Expr.Binary.Operator.OR            -> {
                val l = visit(expr.left).fromRef().unwrap()

                l as? Boolean ?: KBError.invalidLeftOperand(l, expr.op, expr.left.context)

                if (l) return true

                val r = visit(expr.right).fromRef().unwrap()

                r as? Boolean ?: KBError.invalidRightOperand(r, expr.op, expr.right.context)

                return r
            }

            Expr.Binary.Operator.AND           -> {
                val l = visit(expr.left).fromRef().unwrap()

                l as? Boolean ?: KBError.invalidLeftOperand(l, expr.op, expr.left.context)

                if (!l) return false

                val r = visit(expr.right).fromRef().unwrap()

                r as? Boolean ?: KBError.invalidRightOperand(r, expr.op, expr.right.context)

                return r
            }

            Expr.Binary.Operator.EQUAL         -> when (val l = visit(expr.left).fromRef().unwrap()) {
                is Boolean       -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Boolean -> l == r
                    KBEmpty    -> l.isEmptyValue()
                    else       -> false
                }

                is Byte          -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l == r
                    is Short  -> l == r
                    is Int    -> l == r
                    is Long   -> l == r
                    is Float  -> l == r
                    is Double -> l == r
                    KBEmpty   -> l.isEmptyValue()
                    else      -> false
                }

                is Short         -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l == r
                    is Short  -> l == r
                    is Int    -> l == r
                    is Long   -> l == r
                    is Float  -> l == r
                    is Double -> l == r
                    KBEmpty   -> l.isEmptyValue()
                    else      -> false
                }

                is Int           -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l == r
                    is Short  -> l == r
                    is Int    -> l == r
                    is Long   -> l == r
                    is Float  -> l == r
                    is Double -> l == r
                    KBEmpty   -> l.isEmptyValue()
                    else      -> false
                }

                is Long          -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l == r
                    is Short  -> l == r
                    is Int    -> l == r
                    is Long   -> l == r
                    is Float  -> l == r
                    is Double -> l == r
                    KBEmpty   -> l.isEmptyValue()
                    else      -> false
                }

                is Float         -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l == r
                    is Short  -> l == r
                    is Int    -> l == r
                    is Long   -> l == r
                    is Float  -> l == r
                    is Double -> l == r
                    KBEmpty   -> l.isEmptyValue()
                    else      -> false
                }

                is Double        -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l == r
                    is Short  -> l == r
                    is Int    -> l == r
                    is Long   -> l == r
                    is Float  -> l == r
                    is Double -> l == r
                    KBEmpty   -> l.isEmptyValue()
                    else      -> false
                }

                is Char          -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Char -> l == r
                    KBEmpty -> l.isEmptyValue()
                    else    -> false
                }

                is String        -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is String -> l == r
                    KBEmpty   -> l.isEmptyValue()
                    else      -> false
                }

                is ArrayInstance -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is ArrayInstance -> l == r
                    KBEmpty          -> l.isEmpty()
                    else             -> false
                }

                is DataInstance  -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is DataInstance -> l == r
                    KBEmpty         -> l.isEmpty()
                    else            -> false
                }

                else             -> false
            }

            Expr.Binary.Operator.NOT_EQUAL     -> when (val l = visit(expr.left).fromRef().unwrap()) {
                is Boolean       -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Boolean -> l != r
                    KBEmpty    -> !l.isEmptyValue()
                    else       -> true
                }

                is Byte          -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l != r
                    is Short  -> l != r
                    is Int    -> l != r
                    is Long   -> l != r
                    is Float  -> l != r
                    is Double -> l != r
                    KBEmpty   -> !l.isEmptyValue()
                    else      -> true
                }

                is Short         -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l != r
                    is Short  -> l != r
                    is Int    -> l != r
                    is Long   -> l != r
                    is Float  -> l != r
                    is Double -> l != r
                    KBEmpty   -> !l.isEmptyValue()
                    else      -> true
                }

                is Int           -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l != r
                    is Short  -> l != r
                    is Int    -> l != r
                    is Long   -> l != r
                    is Float  -> l != r
                    is Double -> l != r
                    KBEmpty   -> !l.isEmptyValue()
                    else      -> true
                }

                is Long          -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l != r
                    is Short  -> l != r
                    is Int    -> l != r
                    is Long   -> l != r
                    is Float  -> l != r
                    is Double -> l != r
                    KBEmpty   -> !l.isEmptyValue()
                    else      -> true
                }

                is Float         -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l != r
                    is Short  -> l != r
                    is Int    -> l != r
                    is Long   -> l != r
                    is Float  -> l != r
                    is Double -> l != r
                    KBEmpty   -> !l.isEmptyValue()
                    else      -> true
                }

                is Double        -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l != r
                    is Short  -> l != r
                    is Int    -> l != r
                    is Long   -> l != r
                    is Float  -> l != r
                    is Double -> l != r
                    KBEmpty   -> !l.isEmptyValue()
                    else      -> true
                }

                is Char          -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Char -> l != r
                    KBEmpty -> !l.isEmptyValue()
                    else    -> true
                }

                is String        -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is String -> l != r
                    KBEmpty   -> !l.isEmptyValue()
                    else      -> true
                }

                is ArrayInstance -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is ArrayInstance -> l != r
                    KBEmpty          -> !l.isEmpty()
                    else             -> true
                }

                is DataInstance  -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is DataInstance -> l != r
                    KBEmpty         -> !l.isEmpty()
                    else            -> true
                }

                else             -> true
            }

            Expr.Binary.Operator.LESS          -> when (val l = visit(expr.left).fromRef().unwrap()) {
                is Byte   -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l < r
                    is Short  -> l < r
                    is Int    -> l < r
                    is Long   -> l < r
                    is Float  -> l < r
                    is Double -> l < r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Short  -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l < r
                    is Short  -> l < r
                    is Int    -> l < r
                    is Long   -> l < r
                    is Float  -> l < r
                    is Double -> l < r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Int    -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l < r
                    is Short  -> l < r
                    is Int    -> l < r
                    is Long   -> l < r
                    is Float  -> l < r
                    is Double -> l < r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Long   -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l < r
                    is Short  -> l < r
                    is Int    -> l < r
                    is Long   -> l < r
                    is Float  -> l < r
                    is Double -> l < r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Float  -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l < r
                    is Short  -> l < r
                    is Int    -> l < r
                    is Long   -> l < r
                    is Float  -> l < r
                    is Double -> l < r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Double -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l < r
                    is Short  -> l < r
                    is Int    -> l < r
                    is Long   -> l < r
                    is Float  -> l < r
                    is Double -> l < r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Char   -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Char -> l < r
                    else    -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is String -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is String -> l < r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                else      -> KBError.invalidLeftOperand(l, expr.op, expr.left.context)
            }

            Expr.Binary.Operator.LESS_EQUAL    -> when (val l = visit(expr.left).fromRef().unwrap()) {
                is Byte   -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l <= r
                    is Short  -> l <= r
                    is Int    -> l <= r
                    is Long   -> l <= r
                    is Float  -> l <= r
                    is Double -> l <= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Short  -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l <= r
                    is Short  -> l <= r
                    is Int    -> l <= r
                    is Long   -> l <= r
                    is Float  -> l <= r
                    is Double -> l <= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Int    -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l <= r
                    is Short  -> l <= r
                    is Int    -> l <= r
                    is Long   -> l <= r
                    is Float  -> l <= r
                    is Double -> l <= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Long   -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l <= r
                    is Short  -> l <= r
                    is Int    -> l <= r
                    is Long   -> l <= r
                    is Float  -> l <= r
                    is Double -> l <= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Float  -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l <= r
                    is Short  -> l <= r
                    is Int    -> l <= r
                    is Long   -> l <= r
                    is Float  -> l <= r
                    is Double -> l <= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Double -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l <= r
                    is Short  -> l <= r
                    is Int    -> l <= r
                    is Long   -> l <= r
                    is Float  -> l <= r
                    is Double -> l <= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Char   -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Char -> l <= r
                    else    -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is String -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is String -> l <= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                else      -> KBError.invalidLeftOperand(l, expr.op, expr.left.context)
            }

            Expr.Binary.Operator.GREATER       -> when (val l = visit(expr.left).fromRef().unwrap()) {
                is Byte   -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l > r
                    is Short  -> l > r
                    is Int    -> l > r
                    is Long   -> l > r
                    is Float  -> l > r
                    is Double -> l > r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Short  -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l > r
                    is Short  -> l > r
                    is Int    -> l > r
                    is Long   -> l > r
                    is Float  -> l > r
                    is Double -> l > r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Int    -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l > r
                    is Short  -> l > r
                    is Int    -> l > r
                    is Long   -> l > r
                    is Float  -> l > r
                    is Double -> l > r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Long   -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l > r
                    is Short  -> l > r
                    is Int    -> l > r
                    is Long   -> l > r
                    is Float  -> l > r
                    is Double -> l > r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Float  -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l > r
                    is Short  -> l > r
                    is Int    -> l > r
                    is Long   -> l > r
                    is Float  -> l > r
                    is Double -> l > r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Double -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l > r
                    is Short  -> l > r
                    is Int    -> l > r
                    is Long   -> l > r
                    is Float  -> l > r
                    is Double -> l > r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Char   -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Char -> l > r
                    else    -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is String -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is String -> l > r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                else      -> KBError.invalidLeftOperand(l, expr.op, expr.left.context)
            }

            Expr.Binary.Operator.GREATER_EQUAL -> when (val l = visit(expr.left).fromRef().unwrap()) {
                is Byte   -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l >= r
                    is Short  -> l >= r
                    is Int    -> l >= r
                    is Long   -> l >= r
                    is Float  -> l >= r
                    is Double -> l >= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Short  -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l >= r
                    is Short  -> l >= r
                    is Int    -> l >= r
                    is Long   -> l >= r
                    is Float  -> l >= r
                    is Double -> l >= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Int    -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l >= r
                    is Short  -> l >= r
                    is Int    -> l >= r
                    is Long   -> l >= r
                    is Float  -> l >= r
                    is Double -> l >= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Long   -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l >= r
                    is Short  -> l >= r
                    is Int    -> l >= r
                    is Long   -> l >= r
                    is Float  -> l >= r
                    is Double -> l >= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Float  -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l >= r
                    is Short  -> l >= r
                    is Int    -> l >= r
                    is Long   -> l >= r
                    is Float  -> l >= r
                    is Double -> l >= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Double -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l >= r
                    is Short  -> l >= r
                    is Int    -> l >= r
                    is Long   -> l >= r
                    is Float  -> l >= r
                    is Double -> l >= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Char   -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Char -> l >= r
                    else    -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is String -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is String -> l >= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                else      -> KBError.invalidLeftOperand(l, expr.op, expr.left.context)
            }

            Expr.Binary.Operator.IS            -> {
                val value = visit(expr.left).fromRef()

                val type = visit(expr.right).fromRef() as DataType

                type.filter(this, value) != null
            }

            Expr.Binary.Operator.IS_NOT        -> {
                val value = visit(expr.left).fromRef()

                val type = visit(expr.right).fromRef() as DataType

                type.filter(this, value) == null
            }

            Expr.Binary.Operator.CONCAT        -> {
                val l = visit(expr.left).fromRef().unwrap()
                val r = visit(expr.right).fromRef().unwrap()

                "$l$r"
            }

            Expr.Binary.Operator.ADD           -> when (val l = visit(expr.left).fromRef().unwrap()) {
                is Byte         -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> (l + r).toByte()
                    is Short  -> (l + r).toShort()
                    is Int    -> l + r
                    is Long   -> l + r
                    is Float  -> l + r
                    is Double -> l + r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Short        -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> (l + r).toShort()
                    is Short  -> (l + r).toShort()
                    is Int    -> l + r
                    is Long   -> l + r
                    is Float  -> l + r
                    is Double -> l + r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Int          -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l + r
                    is Short  -> l + r
                    is Int    -> l + r
                    is Long   -> l + r
                    is Float  -> l + r
                    is Double -> l + r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Long         -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l + r
                    is Short  -> l + r
                    is Int    -> l + r
                    is Long   -> l + r
                    is Float  -> l + r
                    is Double -> l + r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Float        -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l + r
                    is Short  -> l + r
                    is Int    -> l + r
                    is Long   -> l + r
                    is Float  -> l + r
                    is Double -> l + r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Double       -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l + r
                    is Short  -> l + r
                    is Int    -> l + r
                    is Long   -> l + r
                    is Float  -> l + r
                    is Double -> l + r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Char         -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Int -> l + r
                    else   -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is DataInstance -> l.invokeBinaryOperator(this, expr.op, visit(expr.right).fromRef())

                else            -> KBError.invalidLeftOperand(l, expr.op, expr.left.context)
            }

            Expr.Binary.Operator.SUBTRACT      -> when (val l = visit(expr.left).fromRef().unwrap()) {
                is Byte         -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> (l - r).toByte()
                    is Short  -> (l - r).toShort()
                    is Int    -> l - r
                    is Long   -> l - r
                    is Float  -> l - r
                    is Double -> l - r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Short        -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> (l - r).toShort()
                    is Short  -> (l - r).toShort()
                    is Int    -> l - r
                    is Long   -> l - r
                    is Float  -> l - r
                    is Double -> l - r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Int          -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l - r
                    is Short  -> l - r
                    is Int    -> l - r
                    is Long   -> l - r
                    is Float  -> l - r
                    is Double -> l - r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Long         -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l - r
                    is Short  -> l - r
                    is Int    -> l - r
                    is Long   -> l - r
                    is Float  -> l - r
                    is Double -> l - r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Float        -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l - r
                    is Short  -> l - r
                    is Int    -> l - r
                    is Long   -> l - r
                    is Float  -> l - r
                    is Double -> l - r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Double       -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l - r
                    is Short  -> l - r
                    is Int    -> l - r
                    is Long   -> l - r
                    is Float  -> l - r
                    is Double -> l - r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is DataInstance -> l.invokeBinaryOperator(this, expr.op, visit(expr.right).fromRef())

                else            -> KBError.invalidLeftOperand(l, expr.op, expr.left.context)
            }

            Expr.Binary.Operator.MULTIPLY      -> when (val l = visit(expr.left).fromRef().unwrap()) {
                is Byte         -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> (l * r).toByte()
                    is Short  -> (l * r).toShort()
                    is Int    -> l * r
                    is Long   -> l * r
                    is Float  -> l * r
                    is Double -> l * r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Short        -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> (l * r).toShort()
                    is Short  -> (l * r).toShort()
                    is Int    -> l * r
                    is Long   -> l * r
                    is Float  -> l * r
                    is Double -> l * r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Int          -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l * r
                    is Short  -> l * r
                    is Int    -> l * r
                    is Long   -> l * r
                    is Float  -> l * r
                    is Double -> l * r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Long         -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l * r
                    is Short  -> l * r
                    is Int    -> l * r
                    is Long   -> l * r
                    is Float  -> l * r
                    is Double -> l * r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Float        -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l * r
                    is Short  -> l * r
                    is Int    -> l * r
                    is Long   -> l * r
                    is Float  -> l * r
                    is Double -> l * r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Double       -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l * r
                    is Short  -> l * r
                    is Int    -> l * r
                    is Long   -> l * r
                    is Float  -> l * r
                    is Double -> l * r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Char         -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> buildString { repeat(r.toInt()) { append(l) } }
                    is Short  -> buildString { repeat(r.toInt()) { append(l) } }
                    is Int    -> buildString { repeat(r) { append(l) } }
                    is Long   -> buildString { repeat(r.toInt()) { append(l) } }
                    is Float  -> buildString { repeat(r.toInt()) { append(l) } }
                    is Double -> buildString { repeat(r.toInt()) { append(l) } }
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is String       -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l.repeat(r.toInt())
                    is Short  -> l.repeat(r.toInt())
                    is Int    -> l.repeat(r)
                    is Long   -> l.repeat(r.toInt())
                    is Float  -> l.repeat(r.toInt())
                    is Double -> l.repeat(r.toInt())
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is DataInstance -> l.invokeBinaryOperator(this, expr.op, visit(expr.right).fromRef())

                else            -> KBError.invalidLeftOperand(l, expr.op, expr.left.context)
            }

            Expr.Binary.Operator.DIVIDE        -> when (val l = visit(expr.left).fromRef().unwrap()) {
                is Byte         -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> (l / r).toByte()
                    is Short  -> (l / r).toShort()
                    is Int    -> l / r
                    is Long   -> l / r
                    is Float  -> l / r
                    is Double -> l / r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Short        -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> (l / r).toShort()
                    is Short  -> (l / r).toShort()
                    is Int    -> l / r
                    is Long   -> l / r
                    is Float  -> l / r
                    is Double -> l / r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Int          -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l / r
                    is Short  -> l / r
                    is Int    -> l / r
                    is Long   -> l / r
                    is Float  -> l / r
                    is Double -> l / r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Long         -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l / r
                    is Short  -> l / r
                    is Int    -> l / r
                    is Long   -> l / r
                    is Float  -> l / r
                    is Double -> l / r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Float        -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l / r
                    is Short  -> l / r
                    is Int    -> l / r
                    is Long   -> l / r
                    is Float  -> l / r
                    is Double -> l / r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Double       -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l / r
                    is Short  -> l / r
                    is Int    -> l / r
                    is Long   -> l / r
                    is Float  -> l / r
                    is Double -> l / r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is DataInstance -> l.invokeBinaryOperator(this, expr.op, visit(expr.right).fromRef())

                else            -> KBError.invalidLeftOperand(l, expr.op, expr.left.context)
            }

            Expr.Binary.Operator.MODULUS       -> when (val l = visit(expr.left).fromRef().unwrap()) {
                is Byte         -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> (l % r).toByte()
                    is Short  -> (l % r).toShort()
                    is Int    -> l % r
                    is Long   -> l % r
                    is Float  -> l % r
                    is Double -> l % r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Short        -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> (l % r).toShort()
                    is Short  -> (l % r).toShort()
                    is Int    -> l % r
                    is Long   -> l % r
                    is Float  -> l % r
                    is Double -> l % r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Int          -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l % r
                    is Short  -> l % r
                    is Int    -> l % r
                    is Long   -> l % r
                    is Float  -> l % r
                    is Double -> l % r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Long         -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l % r
                    is Short  -> l % r
                    is Int    -> l % r
                    is Long   -> l % r
                    is Float  -> l % r
                    is Double -> l % r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Float        -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l % r
                    is Short  -> l % r
                    is Int    -> l % r
                    is Long   -> l % r
                    is Float  -> l % r
                    is Double -> l % r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is Double       -> when (val r = visit(expr.right).fromRef().unwrap()) {
                    is Byte   -> l % r
                    is Short  -> l % r
                    is Int    -> l % r
                    is Long   -> l % r
                    is Float  -> l % r
                    is Double -> l % r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.context)
                }

                is DataInstance -> l.invokeBinaryOperator(this, expr.op, visit(expr.right).fromRef())

                else            -> KBError.invalidLeftOperand(l, expr.op, expr.left.context)
            }

            Expr.Binary.Operator.AS            -> {
                val value = visit(expr.left).fromRef()

                val type = visit(expr.right).fromRef() as DataType

                type.cast(this, value) ?: KBError.invalidCast(value, type, expr.context)
            }
        }
    }

    override fun visitGetIndexExpr(expr: Expr.GetIndex): Any {
        return when (val target = visit(expr.target).fromRef().unwrap()) {
            is String        -> when (val index = visit(expr.index).fromRef().unwrap()) {
                is Int -> target[index]

                else   -> KBError.invalidIndex(
                    DataType.infer(this, target),
                    DataType.infer(this, index),
                    expr.index.context
                )
            }

            is ArrayInstance -> when (val index = DataType.Primitive.INT.coerce(visit(expr.index).fromRef().unwrap())) {
                is Int -> target[index]

                else   -> KBError.invalidIndex(
                    DataType.infer(this, target),
                    DataType.infer(this, index),
                    expr.index.context
                )
            }

            is DataInstance  -> when (val index = visit(expr.index).fromRef().unwrap()) {
                is Char   -> target[index.toString()] ?: KBError.noMember(
                    target.name,
                    index.toString(),
                    expr.index.context
                )

                is String -> target[index] ?: KBError.noMember(target.name, index, expr.index.context)

                else      -> KBError.invalidIndex(
                    DataType.infer(this, target),
                    DataType.infer(this, index),
                    expr.index.context
                )
            }

            else             -> KBError.nonIndexedType(DataType.infer(this, target), expr.target.context)
        }
    }

    override fun visitSetIndexExpr(expr: Expr.SetIndex): Any {
        return when (val target = visit(expr.target).fromRef().unwrap()) {
            is ArrayInstance -> when (val index = DataType.Primitive.INT.coerce(visit(expr.index).fromRef().unwrap())) {
                is Int -> {
                    val type = target.type
                    var subExpr = expr.expr

                    if (type is DataType.Data && subExpr is Expr.Instantiate && subExpr.isInferred) {
                        subExpr = subExpr.withTarget(type.name)
                    }

                    val result = visit(subExpr).fromRef().unwrap()
                    val value = type.coerce(result) ?: KBError.mismatchedType(result, type, subExpr.context)

                    if (target.type.filter(this, value) == null) {
                        KBError.mismatchedType(value, target.type, expr.context)
                    }

                    target[index] = value

                    value
                }

                else   -> KBError.invalidIndex(
                    DataType.infer(this, target),
                    DataType.infer(this, index),
                    expr.index.context
                )
            }

            is DataInstance  -> when (val index = visit(expr.index).fromRef().unwrap()) {
                is Char   -> {
                    val ref =
                        target[index.toString()] ?: KBError.noMember(target.name, index.toString(), expr.index.context)

                    val type = ref.type
                    var subExpr = expr.expr

                    if (type is DataType.Data && subExpr is Expr.Instantiate && subExpr.isInferred) {
                        subExpr = subExpr.withTarget(type.name)
                    }

                    val result = visit(subExpr).fromRef().unwrap()
                    val value = type.coerce(result) ?: KBError.mismatchedType(result, type, subExpr.context)

                    when (ref.put(this, value)) {
                        true  -> value

                        false -> KBError.mismatchedType(value, ref.type, expr.context)

                        null  -> KBError.reassignedConstant(expr.index.context)
                    }
                }

                is String -> {
                    val ref = target[index] ?: KBError.noMember(target.name, index, expr.index.context)

                    val type = ref.type
                    var subExpr = expr.expr

                    if (type is DataType.Data && subExpr is Expr.Instantiate && subExpr.isInferred) {
                        subExpr = subExpr.withTarget(type.name)
                    }

                    val result = visit(subExpr).fromRef().unwrap()
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

    override fun visitGetMemberExpr(expr: Expr.GetMember): Any {
        val target = visit(expr.target).fromRef().unwrap()

        if (target is DataInstance) {
            return target[expr.member] ?: KBError.noMember(target.name, expr.member.value, expr.context)
        }

        KBError.nonAccessedType(DataType.infer(this, target), expr.context)
    }

    override fun visitSetMemberExpr(expr: Expr.SetMember): Any {
        val target = visit(expr.target).fromRef().unwrap()

        if (target is DataInstance) {
            target[expr.member]?.put(this, visit(expr.expr).fromRef())

            return Unit
        }

        KBError.nonAccessedType(DataType.infer(this, target), expr.context)
    }

    override fun visitGetEntryExpr(expr: Expr.GetEntry): Any {
        val enum = memory.getEnum(expr.target) ?: TODO("ENUM '${expr.target}' DOES NOT EXIST ${expr.context}")

        if (expr.member.value == "*") {
            return ArrayInstance(DataType.Enum(Expr.Name(Context.none, enum.name)), enum.entries.toMutableList())
        }

        return enum[expr.member]
    }

    override fun visitInvokeExpr(expr: Expr.Invoke): KBValue<*> {
        val (mode, _, value) = performInvoke(expr)

        return when (mode) {
            InvokeResult.Mode.SUCCESS         -> value

            InvokeResult.Mode.FAIL_UNDECLARED -> KBError.undeclaredSub(expr.name, expr.name.context)

            InvokeResult.Mode.FAIL_POSITIONS  -> KBError.unresolvedPositions(expr.name, expr.context)

            InvokeResult.Mode.FAIL_TYPES      -> KBError.unresolvedTypes(expr.name, expr.context)
        }
    }

    private fun performInvoke(expr: Expr.Invoke): InvokeResult {
        val subs = memory.getSubs(expr.name) ?: return InvokeResult(InvokeResult.Mode.FAIL_UNDECLARED)

        val args = expr.args

        val positionValid = mutableListOf<Pair<Stmt.Sub, List<Stmt.Decl>>>()

        for (sub in subs) {
            val resolved = resolvePosition(sub.params, args) ?: continue

            positionValid += sub to resolved
        }

        if (positionValid.isEmpty()) {
            return InvokeResult(InvokeResult.Mode.FAIL_POSITIONS)
        }

        val typeValid = mutableListOf<Pair<Stmt.Sub, List<Stmt.Decl>>>()

        for ((sub, decls) in positionValid) {
            val resolved = resolveType(decls) ?: continue

            typeValid += sub to resolved
        }

        if (typeValid.isEmpty()) {
            return InvokeResult(InvokeResult.Mode.FAIL_TYPES)
        }

        val (sub, decls) = if (typeValid.size == 1)
            typeValid[0]
        else
            typeValid.minByOrNull { (_, decls) -> decls.size }!!

        val type = visit(sub.definition.type).fromRef() as DataType

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
                type.default(this) ?: Unit//KBError.noDefaultValue(sub.type.value, sub.name.location)
            )

            ref = memory.getRef(sub.definition.name)!!

            if (sub.isLinked) {
                val subArgs = mutableListOf<Any>()

                for (name in decls.map { it.definition.name }) {
                    subArgs += memory.getRef(name)!!.fromRef()
                }

                val builtin = library[sub] ?: KBError.noBuiltin(sub.definition.name, sub.context)

                val builtinValue =
                    builtin(this, subArgs) ?: KBError.mismatchedBuiltinType(sub.definition.name, type, sub.context)

                val value = type.filter(this, builtinValue)
                    ?: KBError.mismatchedReturnType(sub.definition.name, type, Context.none)

                return InvokeResult(InvokeResult.Mode.SUCCESS, type, value)
            }

            try {
                visit(sub.body)
            }
            catch (r: Redirect.Yield) {
                val value = type.filter(this, r.value)
                    ?: KBError.mismatchedReturnType(sub.definition.name, type, r.origin)

                return InvokeResult(InvokeResult.Mode.SUCCESS, type, value)
            }
            catch (r: Redirect.Return) {
                return InvokeResult(InvokeResult.Mode.SUCCESS, type, ref.value)
            }
        }
        finally {
            memory.pop()
        }

        val value = type.filter(this, ref.value) ?: KBError.noYield(sub.definition.name, expr.name.context)

        return InvokeResult(InvokeResult.Mode.SUCCESS, type, value)
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
            val varargs = mutableListOf<Expr>()

            while (p < args.size) {
                val (each, expr) = args[p++]

                varargs += if (each) {
                    Expr.Each(expr.context, expr)
                }
                else {
                    expr
                }
            }

            exprs[exprs.lastIndex] = Expr.Array(varargs[0].context, varargs)
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

            val type = visit(decl.definition.type) as DataType
            val value = visit(decl.expr).fromRef().unwrap()

            val finalValue = type.filter(this, type.coerce(value) ?: value) ?: return null

            exprs[i] = finalValue.toExpr()
        }

        return params.mapIndexed { i, decl -> decl.withExpr(exprs[i]) }
    }

    fun invoke(name: String, vararg arguments: Any): InvokeResult? {
        val invoke =
            Expr.Invoke(Context.none, name.toName(), arguments.map { Expr.Invoke.Argument(false, it.toExpr()) })

        val result = performInvoke(invoke)

        return when (result.mode) {
            InvokeResult.Mode.SUCCESS -> result

            else                      -> null
        }
    }

    fun getString(x: Any) =
        (invoke("getstring", x) ?: x.toString()) as? String ?: KBError.mismatchedReturnType(
            "getstring".toName(),
            DataType.Primitive.STRING,
            Context.none
        )


    override fun visitEachExpr(expr: Expr.Each): Any {
        return expr
    }

    fun instantiate(name: String, vararg elements: Any): DataInstance {
        val instantiate =
            Expr.Instantiate(Context.none, name.lowercase().toName(), elements.map { it.toExpr() }.toList())

        return visit(instantiate) as DataInstance
    }

    override fun visitInstantiateExpr(expr: Expr.Instantiate): Any {
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

                    val declType = visit(decl.definition.type)

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

        return DataInstance(data.name, scope)
    }

    data class InvokeResult(val mode: Mode, val type: DataType = DataType.Inferred, val value: KBValue<*> = KBEmpty) {
        enum class Mode {
            SUCCESS,
            FAIL_UNDECLARED,
            FAIL_POSITIONS,
            FAIL_TYPES
        }
    }

    private fun Any.unwrap() = if (this is InvokeResult) value else this
}