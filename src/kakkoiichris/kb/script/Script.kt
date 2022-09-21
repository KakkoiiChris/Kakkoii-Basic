package kakkoiichris.kb.script

import kakkoiichris.kb.lexer.Location
import kakkoiichris.kb.parser.Expr
import kakkoiichris.kb.parser.Stmt
import kakkoiichris.kb.parser.toExpr
import kakkoiichris.kb.parser.toName
import kakkoiichris.kb.util.KBError
import kakkoiichris.kb.util.Source

class Script(private val stmts: List<Stmt>) : Stmt.Visitor<Unit>, Expr.Visitor<Any> {
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
        if (memory.hasRef(stmt.name)) {
            KBError.alreadyDeclaredVariable(stmt.name, stmt.name.location)
        }
        
        var expr = stmt.expr
        var type = visit(stmt.type) as DataType
        
        if (expr is Expr.Instantiate && expr.isInferred && type is DataType.Data) {
            expr = expr.withTarget(type.name)
        }
        
        var value = visit(expr).fromRef()
        
        if (value === Unit) {
            KBError.assignedNone(stmt.expr.location)
        }
        
        if (type === DataType.Inferred) {
            if (value === Empty) {
                KBError.inferEmpty(stmt.location)
            }
            
            type = DataType.infer(this, value)
        }
        
        if (value === Empty) {
            value = type.default(this) ?: KBError.noDefaultValue(type, stmt.expr.location)
        }
        
        value = type.coerce(value) ?: value
        
        if (type.filter(this, value) == null) {
            if (type is DataType.Array && type.mismatchedSize(this, value)) {
                KBError.mismatchedArraySize(stmt.expr.location)
            }
            
            KBError.mismatchedType(value, type, stmt.location)
        }
        
        memory.newRef(stmt.constant, stmt.name, type, value)
    }
    
    override fun visitDeclEachStmt(stmt: Stmt.DeclEach) {
        for ((name, _) in stmt.pairs) {
            if (memory.hasRef(name)) {
                KBError.alreadyDeclaredVariable(name, name.location)
            }
        }
        
        val value = visit(stmt.expr).fromRef()
        
        if (value === Unit) {
            KBError.assignedNone(stmt.expr.location)
        }
        
        for ((i, pair) in stmt.pairs.withIndex()) {
            var subValue = when (value) {
                is String        -> value[i]
                
                is ArrayInstance -> value[i]
                
                is DataInstance  -> value.deref()[i]
                
                else             -> KBError.nonPartitionedType(DataType.infer(this, value), stmt.expr.location)
            }
            
            val (name, type) = pair
            
            var subType = type.value
            
            if (subType === DataType.Inferred) {
                subType = DataType.infer(this, subValue)
            }
            
            subValue = subType.coerce(subValue) ?: subValue
            
            if (subType.filter(this, subValue) == null) {
                KBError.mismatchedType(subValue, subType, stmt.location)
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
            if (!(r.label.isEmptyValue() || r.label == stmt.label)) {
                throw r
            }
        }
    }
    
    override fun visitIfStmt(stmt: Stmt.If) {
        val testValue = visit(stmt.test).fromRef()
        
        testValue as? Boolean ?: KBError.invalidTestExpression(testValue, stmt.test.location)
        
        if (testValue) {
            visit(stmt.body)
            
            return
        }
        
        visit(stmt.elze)
    }
    
    override fun visitSwitchStmt(stmt: Stmt.Switch) {
        val subject = visit(stmt.subject).fromRef()
        
        for (case in stmt.cases) {
            when (case) {
                is Stmt.Switch.Case.Values -> {
                    for (test in case.tests) {
                        if (subject == test) {
                            visit(case.block)
                            
                            return
                        }
                    }
                }
                
                is Stmt.Switch.Case.Type   -> {
                    val type = visit(case.type) as DataType
                    
                    type.filter(this, subject) ?: continue
                    
                    visit(case.block)
                    
                    return
                }
                
                is Stmt.Switch.Case.Else   -> visit(case.block)
            }
        }
    }
    
    override fun visitWhileStmt(stmt: Stmt.While) {
        while (true) {
            val testValue = visit(stmt.test).fromRef()
            
            testValue as? Boolean ?: KBError.invalidTestExpression(testValue, stmt.test.location)
            
            if (!testValue) break
            
            try {
                visit(stmt.body)
            }
            catch (r: Redirect.Break) {
                if (!(r.label.isEmptyValue() || r.label == stmt.label)) {
                    throw r
                }
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
            }
            catch (r: Redirect.Next) {
                continue
            }
            
            val testValue = visit(stmt.test).fromRef()
            
            testValue as? Boolean ?: KBError.invalidTestExpression(testValue, stmt.test.location)
            
            if (testValue) break
        }
    }
    
    override fun visitForCounterStmt(stmt: Stmt.ForCounter) {
        try {
            memory.push("for pointer")
            
            visit(stmt.decl)
            
            val pointer = stmt.decl.name
            
            val test = Expr.Binary(stmt.to.location, Expr.Binary.Operator.LESS, pointer, stmt.to)
            
            val increment =
                Expr.Binary(
                    stmt.step.location,
                    Expr.Binary.Operator.ASSIGN,
                    pointer,
                    Expr.Binary(stmt.step.location, Expr.Binary.Operator.ADD, pointer, stmt.step)
                )
            
            do {
                try {
                    visit(stmt.body)
                }
                catch (r: Redirect.Break) {
                    if (!(r.label.isEmptyValue() || r.label == stmt.label)) {
                        throw r
                    }
                }
                catch (r: Redirect.Next) {
                    if (!r.pointer.isEmptyValue() && r.pointer != stmt.decl.name) {
                        throw r
                    }
                }
                
                visit(increment)
            }
            while (visit(test).fromRef() as Boolean)
        }
        finally {
            memory.pop()
        }
    }
    
    override fun visitForIterateStmt(stmt: Stmt.ForIterate) {
        val iterableValue = visit(stmt.iterable).fromRef()
        
        val iterableType = DataType.infer(this, iterableValue)
        
        val iterable = iterableType.iterable(this, iterableValue) ?: KBError.nonIterableType(iterableType, stmt.iterable.location)
        
        for (x in iterable) {
            try {
                memory.push("for iterate pointer")
                
                val decl = stmt.decl.withValue(x)
                
                visit(decl)
                
                try {
                    visit(stmt.body)
                }
                catch (r: Redirect.Break) {
                    if (!(r.label.isEmptyValue() || r.label == stmt.label)) {
                        throw r
                    }
                }
                catch (r: Redirect.Next) {
                    continue
                }
            }
            finally {
                memory.pop()
            }
        }
    }
    
    override fun visitForIterateEachStmt(stmt: Stmt.ForIterateEach) {
        val iterableValue = visit(stmt.iterable).fromRef()
        
        val iterableType = DataType.infer(this, iterableValue)
        
        val iterable = iterableType.iterable(this, iterableValue) ?: KBError.nonIterableType(iterableType, stmt.iterable.location)
        
        for (x in iterable) {
            try {
                memory.push("for iterate each pointers")
                
                val decl = stmt.decl.withValue(x)
                
                visit(decl)
                
                try {
                    visit(stmt.body)
                }
                catch (r: Redirect.Break) {
                    if (!(r.label.isEmptyValue() || r.label == stmt.label)) {
                        throw r
                    }
                }
                catch (r: Redirect.Next) {
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
            KBError.redeclaredData(stmt.name, stmt.location)
        }
    }
    
    override fun visitSubStmt(stmt: Stmt.Sub) {
        if (!memory.newSub(stmt)) {
            KBError.redeclaredSub(stmt.signature, stmt.location)
        }
        
        stmt.scope = memory.peek() ?: KBError.noScope(stmt.location)
    }
    
    override fun visitBreakStmt(stmt: Stmt.Break) {
        throw Redirect.Break(stmt.location, stmt.destination)
    }
    
    override fun visitNextStmt(stmt: Stmt.Next) {
        throw Redirect.Next(stmt.location, stmt.pointer)
    }
    
    override fun visitReturnStmt(stmt: Stmt.Return) {
        throw Redirect.Return(stmt.location)
    }
    
    override fun visitYieldStmt(stmt: Stmt.Yield) {
        val value = visit(stmt.value).fromRef()
        
        throw Redirect.Yield(stmt.location, value)
    }
    
    override fun visitTypeStmt(stmt: Stmt.Type) {
        if (!memory.newAlias(stmt.alias.value, stmt.type.value)) {
            KBError.redeclaredAlias(stmt.alias, stmt.alias.location)
        }
    }
    
    override fun visitExpressionStmt(stmt: Stmt.Expression) {
        visit(stmt.expr)
    }
    
    override fun visitEmptyExpr(expr: Expr.Empty) =
        Empty
    
    override fun visitValueExpr(expr: Expr.Value) =
        expr.value
    
    override fun visitNameExpr(expr: Expr.Name) =
        memory.getRef(expr) ?: KBError.undeclaredVariable(expr, expr.location)
    
    override fun visitTypeExpr(expr: Expr.Type) =
        DataType.resolveAlias(this, expr.value)
    
    override fun visitArrayExpr(expr: Expr.Array): Any {
        val elements = mutableListOf<Any>()
        
        for (element in expr.elements) {
            elements += visit(element).fromRef()
        }
        
        val type = DataType.infer(this, elements) as DataType.Array
        
        return ArrayInstance(type.subType, elements)
    }
    
    override fun visitUnaryExpr(expr: Expr.Unary): Any {
        return when (expr.op) {
            Expr.Unary.Operator.NEGATE -> when (val e = visit(expr.expr).fromRef()) {
                is Byte          -> (-e).toByte()
                
                is Short         -> (-e).toShort()
                
                is Int           -> -e
                
                is Long          -> -e
                
                is Float         -> -e
                
                is Double        -> -e
                
                is String        -> e.reversed()
                
                is ArrayInstance -> ArrayInstance(e.type, e.reversed().toMutableList())
                
                is DataInstance  -> e.invokeUnaryOperator(this, expr.op)
                
                else             -> KBError.invalidUnaryOperand(e, expr.op, expr.expr.location)
            }
            
            Expr.Unary.Operator.NOT    -> visit(expr.expr).fromRef().isEmptyValue()
            
            Expr.Unary.Operator.LENGTH -> when (val e = visit(expr.expr).fromRef()) {
                is String        -> e.length
                
                is ArrayInstance -> e.size
                
                is DataInstance  -> e.deref().size
                
                else             -> 1
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
                    
                    if (r === Empty) {
                        r = type.default(this) ?: KBError.noDefaultValue(type, expr.left.location)
                    }
                    
                    r = type.coerce(r) ?: r
                    
                    when (l.put(this, r)) {
                        true  -> r
                        
                        false -> {
                            if (type is DataType.Array && type.mismatchedSize(this, r)) {
                                KBError.mismatchedArraySize(expr.right.location)
                            }
                            
                            KBError.mismatchedType(r, type, expr.location)
                        }
                        
                        null  -> KBError.reassignedConstant(expr.location)
                    }
                }
                
                else                      -> KBError.assignedToValue(expr.left.location)
            }
            
            Expr.Binary.Operator.SWAP          -> {
                val l = visit(expr.left) as? Memory.Scope.Reference ?: KBError.assignedToValue(expr.left.location)
                val r = visit(expr.right) as? Memory.Scope.Reference ?: KBError.assignedToValue(expr.right.location)
                
                val lv = r.type.coerce(l.value) ?: l.value
                val rv = l.type.coerce(r.value) ?: r.value
                
                when (l.put(this, rv)) {
                    true  -> Unit
                    
                    false -> KBError.mismatchedType(r, l.type, expr.left.location)
                    
                    null  -> KBError.reassignedConstant(expr.left.location)
                }
                
                when (r.put(this, lv)) {
                    true  -> Unit
                    
                    false -> KBError.mismatchedType(r, l.type, expr.right.location)
                    
                    null  -> KBError.reassignedConstant(expr.right.location)
                }
            }
            
            Expr.Binary.Operator.OR            -> {
                val l = visit(expr.left).fromRef()
                
                l as? Boolean ?: KBError.invalidLeftOperand(l, expr.op, expr.left.location)
                
                if (l) return true
                
                val r = visit(expr.right).fromRef()
                
                r as? Boolean ?: KBError.invalidRightOperand(r, expr.op, expr.right.location)
                
                return r
            }
            
            Expr.Binary.Operator.AND           -> {
                val l = visit(expr.left).fromRef()
                
                l as? Boolean ?: KBError.invalidLeftOperand(l, expr.op, expr.left.location)
                
                if (!l) return false
                
                val r = visit(expr.right).fromRef()
                
                r as? Boolean ?: KBError.invalidRightOperand(r, expr.op, expr.right.location)
                
                return r
            }
            
            Expr.Binary.Operator.EQUAL         -> when (val l = visit(expr.left).fromRef()) {
                is Boolean       -> when (val r = visit(expr.right).fromRef()) {
                    is Boolean -> l == r
                    Empty      -> l.isEmptyValue()
                    else       -> false
                }
                
                is Byte          -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l == r
                    is Short  -> l == r
                    is Int    -> l == r
                    is Long   -> l == r
                    is Float  -> l == r
                    is Double -> l == r
                    Empty     -> l.isEmptyValue()
                    else      -> false
                }
                
                is Short         -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l == r
                    is Short  -> l == r
                    is Int    -> l == r
                    is Long   -> l == r
                    is Float  -> l == r
                    is Double -> l == r
                    Empty     -> l.isEmptyValue()
                    else      -> false
                }
                
                is Int           -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l == r
                    is Short  -> l == r
                    is Int    -> l == r
                    is Long   -> l == r
                    is Float  -> l == r
                    is Double -> l == r
                    Empty     -> l.isEmptyValue()
                    else      -> false
                }
                
                is Long          -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l == r
                    is Short  -> l == r
                    is Int    -> l == r
                    is Long   -> l == r
                    is Float  -> l == r
                    is Double -> l == r
                    Empty     -> l.isEmptyValue()
                    else      -> false
                }
                
                is Float         -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l == r
                    is Short  -> l == r
                    is Int    -> l == r
                    is Long   -> l == r
                    is Float  -> l == r
                    is Double -> l == r
                    Empty     -> l.isEmptyValue()
                    else      -> false
                }
                
                is Double        -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l == r
                    is Short  -> l == r
                    is Int    -> l == r
                    is Long   -> l == r
                    is Float  -> l == r
                    is Double -> l == r
                    Empty     -> l.isEmptyValue()
                    else      -> false
                }
                
                is Char          -> when (val r = visit(expr.right).fromRef()) {
                    is Char -> l == r
                    Empty   -> l.isEmptyValue()
                    else    -> false
                }
                
                is String        -> when (val r = visit(expr.right).fromRef()) {
                    is String -> l == r
                    Empty     -> l.isEmptyValue()
                    else      -> false
                }
                
                is ArrayInstance -> when (val r = visit(expr.right).fromRef()) {
                    is ArrayInstance -> l == r
                    Empty            -> l.isEmpty()
                    else             -> false
                }
                
                is DataInstance  -> when (val r = visit(expr.right).fromRef()) {
                    is DataInstance -> l == r
                    Empty           -> l.isEmpty()
                    else            -> false
                }
                
                else             -> false
            }
            
            Expr.Binary.Operator.NOT_EQUAL     -> when (val l = visit(expr.left).fromRef()) {
                is Boolean       -> when (val r = visit(expr.right).fromRef()) {
                    is Boolean -> l != r
                    Empty      -> !l.isEmptyValue()
                    else       -> true
                }
                
                is Byte          -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l != r
                    is Short  -> l != r
                    is Int    -> l != r
                    is Long   -> l != r
                    is Float  -> l != r
                    is Double -> l != r
                    Empty     -> !l.isEmptyValue()
                    else      -> true
                }
                
                is Short         -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l != r
                    is Short  -> l != r
                    is Int    -> l != r
                    is Long   -> l != r
                    is Float  -> l != r
                    is Double -> l != r
                    Empty     -> !l.isEmptyValue()
                    else      -> true
                }
                
                is Int           -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l != r
                    is Short  -> l != r
                    is Int    -> l != r
                    is Long   -> l != r
                    is Float  -> l != r
                    is Double -> l != r
                    Empty     -> !l.isEmptyValue()
                    else      -> true
                }
                
                is Long          -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l != r
                    is Short  -> l != r
                    is Int    -> l != r
                    is Long   -> l != r
                    is Float  -> l != r
                    is Double -> l != r
                    Empty     -> !l.isEmptyValue()
                    else      -> true
                }
                
                is Float         -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l != r
                    is Short  -> l != r
                    is Int    -> l != r
                    is Long   -> l != r
                    is Float  -> l != r
                    is Double -> l != r
                    Empty     -> !l.isEmptyValue()
                    else      -> true
                }
                
                is Double        -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l != r
                    is Short  -> l != r
                    is Int    -> l != r
                    is Long   -> l != r
                    is Float  -> l != r
                    is Double -> l != r
                    Empty     -> !l.isEmptyValue()
                    else      -> true
                }
                
                is Char          -> when (val r = visit(expr.right).fromRef()) {
                    is Char -> l != r
                    Empty   -> !l.isEmptyValue()
                    else    -> true
                }
                
                is String        -> when (val r = visit(expr.right).fromRef()) {
                    is String -> l != r
                    Empty     -> !l.isEmptyValue()
                    else      -> true
                }
                
                is ArrayInstance -> when (val r = visit(expr.right).fromRef()) {
                    is ArrayInstance -> l != r
                    Empty            -> !l.isEmpty()
                    else             -> true
                }
                
                is DataInstance  -> when (val r = visit(expr.right).fromRef()) {
                    is DataInstance -> l != r
                    Empty           -> !l.isEmpty()
                    else            -> true
                }
                
                else             -> true
            }
            
            Expr.Binary.Operator.LESS          -> when (val l = visit(expr.left).fromRef()) {
                is Byte   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l < r
                    is Short  -> l < r
                    is Int    -> l < r
                    is Long   -> l < r
                    is Float  -> l < r
                    is Double -> l < r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Short  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l < r
                    is Short  -> l < r
                    is Int    -> l < r
                    is Long   -> l < r
                    is Float  -> l < r
                    is Double -> l < r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Int    -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l < r
                    is Short  -> l < r
                    is Int    -> l < r
                    is Long   -> l < r
                    is Float  -> l < r
                    is Double -> l < r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Long   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l < r
                    is Short  -> l < r
                    is Int    -> l < r
                    is Long   -> l < r
                    is Float  -> l < r
                    is Double -> l < r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Float  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l < r
                    is Short  -> l < r
                    is Int    -> l < r
                    is Long   -> l < r
                    is Float  -> l < r
                    is Double -> l < r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Double -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l < r
                    is Short  -> l < r
                    is Int    -> l < r
                    is Long   -> l < r
                    is Float  -> l < r
                    is Double -> l < r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Char   -> when (val r = visit(expr.right).fromRef()) {
                    is Char -> l < r
                    else    -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is String -> when (val r = visit(expr.right).fromRef()) {
                    is String -> l < r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                else      -> KBError.invalidLeftOperand(l, expr.op, expr.left.location)
            }
            
            Expr.Binary.Operator.LESS_EQUAL    -> when (val l = visit(expr.left).fromRef()) {
                is Byte   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l <= r
                    is Short  -> l <= r
                    is Int    -> l <= r
                    is Long   -> l <= r
                    is Float  -> l <= r
                    is Double -> l <= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Short  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l <= r
                    is Short  -> l <= r
                    is Int    -> l <= r
                    is Long   -> l <= r
                    is Float  -> l <= r
                    is Double -> l <= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Int    -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l <= r
                    is Short  -> l <= r
                    is Int    -> l <= r
                    is Long   -> l <= r
                    is Float  -> l <= r
                    is Double -> l <= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Long   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l <= r
                    is Short  -> l <= r
                    is Int    -> l <= r
                    is Long   -> l <= r
                    is Float  -> l <= r
                    is Double -> l <= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Float  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l <= r
                    is Short  -> l <= r
                    is Int    -> l <= r
                    is Long   -> l <= r
                    is Float  -> l <= r
                    is Double -> l <= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Double -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l <= r
                    is Short  -> l <= r
                    is Int    -> l <= r
                    is Long   -> l <= r
                    is Float  -> l <= r
                    is Double -> l <= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Char   -> when (val r = visit(expr.right).fromRef()) {
                    is Char -> l <= r
                    else    -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is String -> when (val r = visit(expr.right).fromRef()) {
                    is String -> l <= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                else      -> KBError.invalidLeftOperand(l, expr.op, expr.left.location)
            }
            
            Expr.Binary.Operator.GREATER       -> when (val l = visit(expr.left).fromRef()) {
                is Byte   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l > r
                    is Short  -> l > r
                    is Int    -> l > r
                    is Long   -> l > r
                    is Float  -> l > r
                    is Double -> l > r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Short  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l > r
                    is Short  -> l > r
                    is Int    -> l > r
                    is Long   -> l > r
                    is Float  -> l > r
                    is Double -> l > r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Int    -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l > r
                    is Short  -> l > r
                    is Int    -> l > r
                    is Long   -> l > r
                    is Float  -> l > r
                    is Double -> l > r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Long   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l > r
                    is Short  -> l > r
                    is Int    -> l > r
                    is Long   -> l > r
                    is Float  -> l > r
                    is Double -> l > r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Float  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l > r
                    is Short  -> l > r
                    is Int    -> l > r
                    is Long   -> l > r
                    is Float  -> l > r
                    is Double -> l > r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Double -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l > r
                    is Short  -> l > r
                    is Int    -> l > r
                    is Long   -> l > r
                    is Float  -> l > r
                    is Double -> l > r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Char   -> when (val r = visit(expr.right).fromRef()) {
                    is Char -> l > r
                    else    -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is String -> when (val r = visit(expr.right).fromRef()) {
                    is String -> l > r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                else      -> KBError.invalidLeftOperand(l, expr.op, expr.left.location)
            }
            
            Expr.Binary.Operator.GREATER_EQUAL -> when (val l = visit(expr.left).fromRef()) {
                is Byte   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l >= r
                    is Short  -> l >= r
                    is Int    -> l >= r
                    is Long   -> l >= r
                    is Float  -> l >= r
                    is Double -> l >= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Short  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l >= r
                    is Short  -> l >= r
                    is Int    -> l >= r
                    is Long   -> l >= r
                    is Float  -> l >= r
                    is Double -> l >= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Int    -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l >= r
                    is Short  -> l >= r
                    is Int    -> l >= r
                    is Long   -> l >= r
                    is Float  -> l >= r
                    is Double -> l >= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Long   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l >= r
                    is Short  -> l >= r
                    is Int    -> l >= r
                    is Long   -> l >= r
                    is Float  -> l >= r
                    is Double -> l >= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Float  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l >= r
                    is Short  -> l >= r
                    is Int    -> l >= r
                    is Long   -> l >= r
                    is Float  -> l >= r
                    is Double -> l >= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Double -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l >= r
                    is Short  -> l >= r
                    is Int    -> l >= r
                    is Long   -> l >= r
                    is Float  -> l >= r
                    is Double -> l >= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Char   -> when (val r = visit(expr.right).fromRef()) {
                    is Char -> l >= r
                    else    -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is String -> when (val r = visit(expr.right).fromRef()) {
                    is String -> l >= r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                else      -> KBError.invalidLeftOperand(l, expr.op, expr.left.location)
            }
            
            Expr.Binary.Operator.IS            -> {
                val value = visit(expr.left).fromRef()
                
                val type = visit(expr.right).fromRef() as DataType
                
                type.filter(this, value) != null
            }
            
            Expr.Binary.Operator.CONCAT        -> {
                val l = visit(expr.left).fromRef()
                val r = visit(expr.right).fromRef()
                
                "$l$r"
            }
            
            Expr.Binary.Operator.ADD           -> when (val l = visit(expr.left).fromRef()) {
                is Byte         -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> (l + r).toByte()
                    is Short  -> (l + r).toShort()
                    is Int    -> l + r
                    is Long   -> l + r
                    is Float  -> l + r
                    is Double -> l + r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Short        -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> (l + r).toShort()
                    is Short  -> (l + r).toShort()
                    is Int    -> l + r
                    is Long   -> l + r
                    is Float  -> l + r
                    is Double -> l + r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Int          -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l + r
                    is Short  -> l + r
                    is Int    -> l + r
                    is Long   -> l + r
                    is Float  -> l + r
                    is Double -> l + r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Long         -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l + r
                    is Short  -> l + r
                    is Int    -> l + r
                    is Long   -> l + r
                    is Float  -> l + r
                    is Double -> l + r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Float        -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l + r
                    is Short  -> l + r
                    is Int    -> l + r
                    is Long   -> l + r
                    is Float  -> l + r
                    is Double -> l + r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Double       -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l + r
                    is Short  -> l + r
                    is Int    -> l + r
                    is Long   -> l + r
                    is Float  -> l + r
                    is Double -> l + r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Char         -> when (val r = visit(expr.right).fromRef()) {
                    is Int -> l + r
                    else   -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is DataInstance -> l.invokeBinaryOperator(this, expr.op, visit(expr.right).fromRef())
                
                else            -> KBError.invalidLeftOperand(l, expr.op, expr.left.location)
            }
            
            Expr.Binary.Operator.SUBTRACT      -> when (val l = visit(expr.left).fromRef()) {
                is Byte         -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> (l - r).toByte()
                    is Short  -> (l - r).toShort()
                    is Int    -> l - r
                    is Long   -> l - r
                    is Float  -> l - r
                    is Double -> l - r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Short        -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> (l - r).toShort()
                    is Short  -> (l - r).toShort()
                    is Int    -> l - r
                    is Long   -> l - r
                    is Float  -> l - r
                    is Double -> l - r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Int          -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l - r
                    is Short  -> l - r
                    is Int    -> l - r
                    is Long   -> l - r
                    is Float  -> l - r
                    is Double -> l - r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Long         -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l - r
                    is Short  -> l - r
                    is Int    -> l - r
                    is Long   -> l - r
                    is Float  -> l - r
                    is Double -> l - r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Float        -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l - r
                    is Short  -> l - r
                    is Int    -> l - r
                    is Long   -> l - r
                    is Float  -> l - r
                    is Double -> l - r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Double       -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l - r
                    is Short  -> l - r
                    is Int    -> l - r
                    is Long   -> l - r
                    is Float  -> l - r
                    is Double -> l - r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is DataInstance -> l.invokeBinaryOperator(this, expr.op, visit(expr.right).fromRef())
                
                else            -> KBError.invalidLeftOperand(l, expr.op, expr.left.location)
            }
            
            Expr.Binary.Operator.MULTIPLY      -> when (val l = visit(expr.left).fromRef()) {
                is Byte         -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> (l * r).toByte()
                    is Short  -> (l * r).toShort()
                    is Int    -> l * r
                    is Long   -> l * r
                    is Float  -> l * r
                    is Double -> l * r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Short        -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> (l * r).toShort()
                    is Short  -> (l * r).toShort()
                    is Int    -> l * r
                    is Long   -> l * r
                    is Float  -> l * r
                    is Double -> l * r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Int          -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l * r
                    is Short  -> l * r
                    is Int    -> l * r
                    is Long   -> l * r
                    is Float  -> l * r
                    is Double -> l * r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Long         -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l * r
                    is Short  -> l * r
                    is Int    -> l * r
                    is Long   -> l * r
                    is Float  -> l * r
                    is Double -> l * r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Float        -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l * r
                    is Short  -> l * r
                    is Int    -> l * r
                    is Long   -> l * r
                    is Float  -> l * r
                    is Double -> l * r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Double       -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l * r
                    is Short  -> l * r
                    is Int    -> l * r
                    is Long   -> l * r
                    is Float  -> l * r
                    is Double -> l * r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is DataInstance -> l.invokeBinaryOperator(this, expr.op, visit(expr.right).fromRef())
                
                else            -> KBError.invalidLeftOperand(l, expr.op, expr.left.location)
            }
            
            Expr.Binary.Operator.DIVIDE        -> when (val l = visit(expr.left).fromRef()) {
                is Byte         -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> (l / r).toByte()
                    is Short  -> (l / r).toShort()
                    is Int    -> l / r
                    is Long   -> l / r
                    is Float  -> l / r
                    is Double -> l / r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Short        -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> (l / r).toShort()
                    is Short  -> (l / r).toShort()
                    is Int    -> l / r
                    is Long   -> l / r
                    is Float  -> l / r
                    is Double -> l / r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Int          -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l / r
                    is Short  -> l / r
                    is Int    -> l / r
                    is Long   -> l / r
                    is Float  -> l / r
                    is Double -> l / r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Long         -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l / r
                    is Short  -> l / r
                    is Int    -> l / r
                    is Long   -> l / r
                    is Float  -> l / r
                    is Double -> l / r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Float        -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l / r
                    is Short  -> l / r
                    is Int    -> l / r
                    is Long   -> l / r
                    is Float  -> l / r
                    is Double -> l / r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Double       -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l / r
                    is Short  -> l / r
                    is Int    -> l / r
                    is Long   -> l / r
                    is Float  -> l / r
                    is Double -> l / r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is DataInstance -> l.invokeBinaryOperator(this, expr.op, visit(expr.right).fromRef())
                
                else            -> KBError.invalidLeftOperand(l, expr.op, expr.left.location)
            }
            
            Expr.Binary.Operator.MODULUS       -> when (val l = visit(expr.left).fromRef()) {
                is Byte         -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> (l % r).toByte()
                    is Short  -> (l % r).toShort()
                    is Int    -> l % r
                    is Long   -> l % r
                    is Float  -> l % r
                    is Double -> l % r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Short        -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> (l % r).toShort()
                    is Short  -> (l % r).toShort()
                    is Int    -> l % r
                    is Long   -> l % r
                    is Float  -> l % r
                    is Double -> l % r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Int          -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l % r
                    is Short  -> l % r
                    is Int    -> l % r
                    is Long   -> l % r
                    is Float  -> l % r
                    is Double -> l % r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Long         -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l % r
                    is Short  -> l % r
                    is Int    -> l % r
                    is Long   -> l % r
                    is Float  -> l % r
                    is Double -> l % r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Float        -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l % r
                    is Short  -> l % r
                    is Int    -> l % r
                    is Long   -> l % r
                    is Float  -> l % r
                    is Double -> l % r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is Double       -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l % r
                    is Short  -> l % r
                    is Int    -> l % r
                    is Long   -> l % r
                    is Float  -> l % r
                    is Double -> l % r
                    else      -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                is DataInstance -> l.invokeBinaryOperator(this, expr.op, visit(expr.right).fromRef())
                
                else            -> KBError.invalidLeftOperand(l, expr.op, expr.left.location)
            }
            
            Expr.Binary.Operator.AS            -> {
                val value = visit(expr.left).fromRef()
                
                val type = visit(expr.right).fromRef() as DataType
                
                type.cast(this, value) ?: KBError.invalidCast(value, type, expr.location)
            }
            
            Expr.Binary.Operator.DOT           -> when (val l = visit(expr.left).fromRef()) {
                is DataInstance -> when (val r = expr.right) {
                    is Expr.Name -> l[r] ?: KBError.noMember(l.name, r.value, expr.right.location)
                    
                    else         -> KBError.invalidRightOperand(r, expr.op, expr.right.location)
                }
                
                else            -> KBError.invalidLeftOperand(l, expr.op, expr.left.location)
            }
        }
    }
    
    override fun visitGetIndexExpr(expr: Expr.GetIndex): Any {
        return when (val target = visit(expr.target).fromRef()) {
            is String        -> when (val index = visit(expr.index).fromRef()) {
                is Int -> target[index]
                
                else   -> KBError.invalidIndex(DataType.infer(this, target), DataType.infer(this, index), expr.index.location)
            }
            
            is ArrayInstance -> when (val index = DataType.Primitive.INT.coerce(visit(expr.index).fromRef())) {
                is Int -> target[index]
                
                else   -> KBError.invalidIndex(DataType.infer(this, target), DataType.infer(this, index), expr.index.location)
            }
            
            is DataInstance  -> when (val index = visit(expr.index).fromRef()) {
                is Char   -> target[index.toString()] ?: KBError.noMember(target.name, index.toString(), expr.index.location)
                
                is String -> target[index] ?: KBError.noMember(target.name, index, expr.index.location)
                
                else      -> KBError.invalidIndex(DataType.infer(this, target), DataType.infer(this, index), expr.index.location)
            }
            
            else             -> KBError.nonIndexedType(DataType.infer(this, target), expr.target.location)
        }
    }
    
    override fun visitSetIndexExpr(expr: Expr.SetIndex): Any {
        return when (val target = visit(expr.target).fromRef()) {
            is ArrayInstance -> when (val index = DataType.Primitive.INT.coerce(visit(expr.index).fromRef())) {
                is Int -> {
                    val type = target.type
                    var subExpr = expr.expr
                    
                    if (type is DataType.Data && subExpr is Expr.Instantiate && subExpr.isInferred) {
                        subExpr = subExpr.withTarget(type.name)
                    }
                    
                    val result = visit(subExpr).fromRef()
                    val value = type.coerce(result) ?: TODO("COERCE ARRAY INDEX")
                    
                    if (target.type.filter(this, value) == null) {
                        KBError.mismatchedType(value, target.type, expr.location)
                    }
                    
                    target[index] = value
                    
                    value
                }
                
                else   -> KBError.invalidIndex(DataType.infer(this, target), DataType.infer(this, index), expr.index.location)
            }
            
            is DataInstance  -> when (val index = visit(expr.index).fromRef()) {
                is Char   -> {
                    val ref = target[index.toString()] ?: KBError.noMember(target.name, index.toString(), expr.index.location)
                    
                    val type = ref.type
                    var subExpr = expr.expr
                    
                    if (type is DataType.Data && subExpr is Expr.Instantiate && subExpr.isInferred) {
                        subExpr = subExpr.withTarget(type.name)
                    }
                    
                    val result = visit(subExpr).fromRef()
                    val value = type.coerce(result) ?: TODO("COERCE DATA INDEX CHAR")
                    
                    when (ref.put(this, value)) {
                        true  -> value
                        
                        false -> KBError.mismatchedType(value, ref.type, expr.location)
                        
                        null  -> KBError.reassignedConstant(expr.index.location)
                    }
                }
                
                is String -> {
                    val ref = target[index] ?: KBError.noMember(target.name, index, expr.index.location)
                    
                    val type = ref.type
                    var subExpr = expr.expr
                    
                    if (type is DataType.Data && subExpr is Expr.Instantiate && subExpr.isInferred) {
                        subExpr = subExpr.withTarget(type.name)
                    }
                    
                    val result = visit(subExpr).fromRef()
                    val value = type.coerce(result) ?: TODO("COERCE DATA INDEX STRING")
                    
                    when (ref.put(this, value)) {
                        true  -> value
                        
                        false -> KBError.mismatchedType(value, ref.type, expr.location)
                        
                        null  -> KBError.reassignedConstant(expr.index.location)
                    }
                }
                
                else      -> KBError.invalidIndex(DataType.infer(this, target), DataType.infer(this, index), expr.index.location)
            }
            
            else             -> KBError.nonIndexedType(DataType.infer(this, target), expr.target.location)
        }
    }
    
    override fun visitGetMemberExpr(expr: Expr.GetMember): Any {
        val target = visit(expr.target).fromRef()
        
        if (target is DataInstance) {
            return target[expr.member] ?: KBError.noMember(target.name, expr.member.value, expr.location)
        }
        
        KBError.nonAccessedType(DataType.infer(this, target), expr.location)
    }
    
    override fun visitSetMemberExpr(expr: Expr.SetMember): Any {
        val target = visit(expr.target).fromRef()
        
        if (target is DataInstance) {
            target[expr.member]?.put(this, visit(expr.expr).fromRef())
            
            return Unit
        }
        
        KBError.nonAccessedType(DataType.infer(this, target), expr.location)
    }
    
    fun getString(x: Any) =
        (invoke("getstring", x) ?: x.toString()) as? String ?: TODO("GETSTRING IS NOT A STRING")
    
    fun invoke(name: String, vararg arguments: Any): Any? {
        val invoke = Expr.Invoke(Location.none, name.toName(), arguments.map(Any::toExpr))
        
        val (value, result) = performInvoke(invoke)
        
        return when (result) {
            InvokeResult.SUCCESS -> value.fromRef()
            
            else                 -> null
        }
    }
    
    override fun visitInvokeExpr(expr: Expr.Invoke): Any {
        val (value, result) = performInvoke(expr)
        
        return when (result) {
            InvokeResult.SUCCESS         -> value
            
            InvokeResult.FAIL_UNDECLARED -> KBError.undeclaredSub(expr.name, expr.location)
            
            InvokeResult.FAIL_POSITIONS  -> KBError.unresolvedPositions(expr.name, expr.location)
            
            InvokeResult.FAIL_TYPES      -> KBError.unresolvedTypes(expr.name, expr.location)
        }
    }
    
    private fun resolvePosition(params: List<Stmt.Decl>, args: List<Expr>): List<Stmt.Decl>? {
        val isVararg = params.isNotEmpty() && params.last().isVararg
        
        if (args.size > params.size && !isVararg) {
            return null
        }
        
        val exprs = MutableList(params.size) { i -> params[i].expr }
        
        var p = 0
        
        for (i in exprs.indices) {
            if (p !in args.indices) continue
            
            if (params[i].isVararg) continue
            
            if (args[p] !is Expr.Empty) {
                exprs[i] = args[p]
            }
            
            p++
        }
        
        if (p in args.indices) {
            val varargs = mutableListOf<Expr>()
            
            while (p < args.size) {
                val arg = args[p++]
                
                varargs += arg/*if (arg.spread) {
                    Expr.Spread(arg.location, arg.expr)
                }
                else {
                    arg.expr
                }*/
            }
            
            exprs[exprs.lastIndex] = Expr.Array(varargs[0].location, varargs)
        }
        
        if (isVararg && exprs.last() is Expr.Empty) {
            exprs[exprs.lastIndex] = Expr.Array(Location.none, emptyList())
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
            
            val type = visit(decl.type) as DataType
            val value = visit(decl.expr).fromRef()
            
            val finalValue = type.filter(this, type.coerce(value) ?: value) ?: return null
            
            exprs[i] = finalValue.toExpr()
        }
        
        return params.mapIndexed { i, decl -> decl.withExpr(exprs[i]) }
    }
    
    private enum class InvokeResult {
        SUCCESS,
        FAIL_UNDECLARED,
        FAIL_POSITIONS,
        FAIL_TYPES
    }
    
    private fun performInvoke(expr: Expr.Invoke): Pair<Any, InvokeResult> {
        val subs = memory.getSubs(expr.name) ?: return Unit to InvokeResult.FAIL_UNDECLARED
        
        val args = expr.args
        
        val positionValid = mutableListOf<Pair<Stmt.Sub, List<Stmt.Decl>>>()
        
        for (sub in subs) {
            val resolved = resolvePosition(sub.params, args) ?: continue
            
            positionValid += sub to resolved
        }
        
        if (positionValid.isEmpty()) {
            return Unit to InvokeResult.FAIL_POSITIONS
        }
        
        val typeValid = mutableListOf<Pair<Stmt.Sub, List<Stmt.Decl>>>()
        
        for ((sub, decls) in positionValid) {
            val resolved = resolveType(decls) ?: continue
            
            typeValid += sub to resolved
        }
        
        if (typeValid.isEmpty()) {
            return Unit to InvokeResult.FAIL_TYPES
        }
        
        val (sub, decls) = if (typeValid.size == 1)
            typeValid[0]
        else
            typeValid.minByOrNull { (_, decls) -> decls.size }!!
        
        
        val type = visit(sub.type).fromRef() as DataType
        
        val scope = Memory.Scope(sub.name.value, sub.scope)
        
        val ref: Memory.Scope.Reference
        
        try {
            memory.push(scope)
            
            for (decl in decls) {
                visit(decl)
            }
            
            memory.newRef(
                false,
                sub.name,
                type,
                type.default(this) ?: Unit//KBError.noDefaultValue(sub.type.value, sub.name.location)
            )
            
            ref = memory.getRef(sub.name)!!
            
            if (sub.isLinked) {
                val subArgs = mutableListOf<Any>()
                
                for (name in decls.map { it.name }) {
                    subArgs += memory.getRef(name)!!.fromRef()
                }
                
                val builtin = library[sub] ?: KBError.noBuiltin(sub.name, sub.location)
                
                val builtinValue = builtin(this, subArgs) ?: KBError.mismatchedBuiltinType(sub.name, type, sub.location)
                
                val value = type.filter(this, builtinValue) ?: KBError.mismatchedReturnType(sub.name, type, Location.none)
                
                return value to InvokeResult.SUCCESS
            }
            
            try {
                visit(sub.body)
            }
            catch (r: Redirect.Yield) {
                val value = type.filter(this, r.value) ?: KBError.mismatchedReturnType(sub.name, type, r.origin)
                
                return value to InvokeResult.SUCCESS
            }
            catch (r: Redirect.Return) {
                return ref.value to InvokeResult.SUCCESS
            }
        }
        finally {
            memory.pop()
        }
        
        val value = type.filter(this, ref.value) ?: KBError.noYield(sub.name, expr.name.location)
        
        return value to InvokeResult.SUCCESS
    }
    
    fun instantiate(name: String, vararg elements: Any): DataInstance {
        val instantiate = Expr.Instantiate(Location.none, name.lowercase().toName(), elements.map { it.toExpr() }.toList())
        
        return visit(instantiate) as DataInstance
    }
    
    override fun visitInstantiateExpr(expr: Expr.Instantiate): Any {
        if (expr.isInferred) {
            KBError.emptyInstantiationTarget(expr.location)
        }
        
        val data = memory.getData(expr.target) ?: KBError.undeclaredData(expr.target, expr.target.location)
        
        val scope = Memory.Scope(data.name.value, memory.peek())
        
        try {
            memory.push(scope)
            
            for (i in data.decls.indices) {
                var decl = data.decls[i]
                
                if (i in expr.elements.indices) {
                    var element = expr.elements[i]
                    
                    val declType = visit(decl.type)
                    
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
}