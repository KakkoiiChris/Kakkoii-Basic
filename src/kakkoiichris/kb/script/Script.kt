package kakkoiichris.kb.script

import kakkoiichris.kb.lexer.Location
import kakkoiichris.kb.lexer.Token.Type.*
import kakkoiichris.kb.parser.Expr
import kakkoiichris.kb.parser.Stmt
import kakkoiichris.kb.parser.toExpr
import kakkoiichris.kb.parser.toName
import kakkoiichris.kb.util.KBError
import kakkoiichris.kb.util.Source

class Script(private val stmts: List<Stmt>) : Stmt.Visitor<Unit>, Expr.Visitor<Any> {
    val memory = Memory()
    
    private val core = Memory.Scope("core")
    private val global = Memory.Scope("global", core)
    
    private val library = StandardLibrary()
    
    fun run() {
        val coreSource = Source.readLocal("/core.kb")
        
        val coreStmts = coreSource.compile()
        
        try {
            memory.push(core)
            
            for (stmt in coreStmts) {
                visit(stmt)
            }
        }
        finally {
            memory.pop()
        }
        
        try {
            memory.push(global)
            
            for (stmt in stmts) {
                visit(stmt)
            }
        }
        finally {
            memory.pop()
        }
    }
    
    override fun visitNoneStmt(stmt: Stmt.None) =
        Unit
    
    override fun visitDeclStmt(stmt: Stmt.Decl) {
        var value = visit(stmt.expr).fromRef()
        
        if (value === Unit) {
            KBError.forScript("Cannot assign void to a variable!", stmt.expr.loc)
        }
        
        var type = stmt.type.value
        
        if (type === DataType.Inferred) {
            type = DataType.infer(this, value)
        }
        
        if (value === Empty) {
            value = type.default(this)
                ?: KBError.forScript("No default value for data type '$type'!", stmt.expr.loc)
        }
        
        if (type.matches(this, value) == null) {
            KBError.forScript("Assigned value '$value' is not of type '${type}'!", stmt.loc)
        }
        
        if (!memory.new(stmt.constant, stmt.name, type, value)) {
            KBError.forScript("Variable '${stmt.name.value}' is already declared!", stmt.name.loc)
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
        visit(stmt.body)
    }
    
    override fun visitIfStmt(stmt: Stmt.If) {
        for ((test, body) in stmt.branches) {
            val testValue = visit(test).fromRef()
            
            testValue as? Boolean
                ?: KBError.forScript("Test expression '$testValue' is invalid; must be a bool!", test.loc)
            
            if (testValue) {
                visit(body)
                
                return
            }
        }
        
        visit(stmt.elze)
    }
    
    override fun visitWhileStmt(stmt: Stmt.While) {
        while (true) {
            val testValue = visit(stmt.test).fromRef()
            
            testValue as? Boolean
                ?: KBError.forScript("Test expression '$testValue' is invalid; must be a bool!", stmt.test.loc)
            
            if (!testValue) {
                break
            }
            
            try {
                visit(stmt.body)
            }
            catch (r: Redirect.Break) {
                break
            }
            catch (r: Redirect.Next) {
            }
        }
    }
    
    override fun visitForStmt(stmt: Stmt.For) {
        try {
            memory.push("for pointer")
            
            visit(stmt.decl)
            
            val pointer = stmt.decl.name
            
            val test = Expr.Binary(stmt.to.loc, LSS, pointer, stmt.to)
            
            val increment =
                Expr.Binary(stmt.step.loc, ASN, pointer, Expr.Binary(stmt.step.loc, ADD, pointer, stmt.step))
            
            do {
                try {
                    visit(stmt.body)
                }
                catch (r: Redirect.Break) {
                    break
                }
                catch (r: Redirect.Next) {
                }
                
                visit(increment)
            }
            while (visit(test).fromRef() as Boolean)
        }
        finally {
            memory.pop()
        }
    }
    
    override fun visitForeachStmt(stmt: Stmt.Foreach) {
        try {
            memory.push("for each pointer")
            
            val iterableValue = visit(stmt.iterable).fromRef()
            
            val iterableType = DataType.infer(this, iterableValue)
            
            val iterable = iterableType.iterable(this, iterableValue) ?: TODO()
            
            val decl = if (stmt.decl.type.value == DataType.Inferred)
                stmt.decl.withNewType(iterableType.iterableType ?: TODO())
            else
                stmt.decl
            
            visit(decl)
            
            val pointer = decl.name
            
            for (x in iterable) {
                val assign = Expr.Binary(stmt.iterable.loc, ASN, pointer, x.toExpr())
                
                visit(assign)
                
                visit(stmt.body)
            }
        }
        finally {
            memory.pop()
        }
    }
    
    override fun visitDataStmt(stmt: Stmt.Data) {
        if (!memory.newLet(stmt.name, DataType.Primitive.ANY, stmt)) {
            KBError.forScript("Name '${stmt.name}' has already been declared in this scope!", stmt.name.loc)
        }
    }
    
    override fun visitSubStmt(stmt: Stmt.Sub) {
        if (!memory.newSub(stmt)) {
            KBError.forScript("Sub with signature '${stmt.fullSignature}' already declared!", stmt.loc)
        }
    }
    
    override fun visitBreakStmt(stmt: Stmt.Break) {
        throw Redirect.Break(stmt.loc)
    }
    
    override fun visitNextStmt(stmt: Stmt.Next) {
        throw Redirect.Next(stmt.loc, stmt.pointer)
    }
    
    override fun visitReturnStmt(stmt: Stmt.Return) {
        throw Redirect.Return(stmt.loc)
    }
    
    override fun visitYieldStmt(stmt: Stmt.Yield) {
        val value = visit(stmt.value).fromRef()
        
        throw Redirect.Yield(stmt.loc, value)
    }
    
    override fun visitExpressionStmt(stmt: Stmt.Expression) {
        visit(stmt.expr)
    }
    
    override fun visitNoneExpr(expr: Expr.None) =
        Empty
    
    override fun visitValueExpr(expr: Expr.Value) =
        expr.value
    
    override fun visitNameExpr(expr: Expr.Name) =
        memory.getRef(expr) ?: KBError.forScript("Name '$expr' has not been declared in this scope!", expr.loc)
    
    override fun visitTypeExpr(expr: Expr.Type) =
        expr.value
    
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
            SUB  -> when (val e = visit(expr.expr).fromRef()) {
                is Byte   -> -e
                
                is Short  -> -e
                
                is Int    -> -e
                
                is Long   -> -e
                
                is Float  -> -e
                
                is Double -> -e
                
                else      -> KBError.forScript("Operand '$e' is invalid for unary '${expr.op}' operator!",
                    expr.expr.loc)
            }
            
            NOT  -> when (val e = visit(expr.expr).fromRef()) {
                is Boolean -> !e
                
                else       -> KBError.forScript("Operand '$e' is invalid for unary '${expr.op}' operator!",
                    expr.expr.loc)
            }
            
            LEN  -> when (val e = visit(expr.expr).fromRef()) {
                is String        -> e.length
                
                is ArrayInstance -> e.size
                
                is DataInstance  -> e.deref().size
                
                else             -> 1
            }
            
            else -> error("Broken unary operator '${expr.op}'!")
        }
    }
    
    override fun visitBinaryExpr(expr: Expr.Binary): Any {
        return when (expr.op) {
            ASN  -> when (val l = visit(expr.left)) {
                is Memory.Scope.Reference -> {
                    if (l.constant) {
                        KBError.forScript("Constants cannot be reassigned!", expr.left.loc)
                    }
                    
                    val r = visit(expr.right).fromRef()
                    
                    if (l.type.matches(this, r) == null) {
                        KBError.forScript("Assigned value '$r' does not match variable type '${l.type}'!",
                            expr.right.loc)
                    }
                    
                    l.value = r
                }
                
                else                      -> KBError.forScript("Value '$l' cannot be assigned to!", expr.left.loc)
            }
            
            ORR  -> {
                val l = visit(expr.left).fromRef()
                
                l as? Boolean
                    ?: KBError.forScript("Left operand '$l' is invalid for binary '${expr.op}' operator!",
                        expr.left.loc)
                
                if (l) {
                    return true
                }
                
                val r = visit(expr.right).fromRef()
                
                r as? Boolean
                    ?: KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                
                return r
            }
            
            AND  -> {
                val l = visit(expr.left).fromRef()
                
                l as? Boolean
                    ?: KBError.forScript("Left operand '$l' is invalid for binary '${expr.op}' operator!",
                        expr.left.loc)
                
                if (!l) {
                    return false
                }
                
                val r = visit(expr.right).fromRef()
                
                r as? Boolean
                    ?: KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                
                return r
            }
            
            EQU  -> when (val l = visit(expr.left).fromRef()) {
                is Boolean -> when (val r = visit(expr.right).fromRef()) {
                    is Boolean -> l == r
                    else       -> false
                }
                is Byte    -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l == r
                    is Short  -> l == r
                    is Int    -> l == r
                    is Long   -> l == r
                    is Float  -> l == r
                    is Double -> l == r
                    else      -> false
                }
                is Short   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l == r
                    is Short  -> l == r
                    is Int    -> l == r
                    is Long   -> l == r
                    is Float  -> l == r
                    is Double -> l == r
                    else      -> false
                }
                is Int     -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l == r
                    is Short  -> l == r
                    is Int    -> l == r
                    is Long   -> l == r
                    is Float  -> l == r
                    is Double -> l == r
                    else      -> false
                }
                is Long    -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l == r
                    is Short  -> l == r
                    is Int    -> l == r
                    is Long   -> l == r
                    is Float  -> l == r
                    is Double -> l == r
                    else      -> false
                }
                is Float   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l == r
                    is Short  -> l == r
                    is Int    -> l == r
                    is Long   -> l == r
                    is Float  -> l == r
                    is Double -> l == r
                    else      -> false
                }
                is Double  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l == r
                    is Short  -> l == r
                    is Int    -> l == r
                    is Long   -> l == r
                    is Float  -> l == r
                    is Double -> l == r
                    else      -> false
                }
                is Char    -> when (val r = visit(expr.right).fromRef()) {
                    is Char -> l == r
                    else    -> false
                }
                is String  -> when (val r = visit(expr.right).fromRef()) {
                    is String -> l == r
                    else      -> false
                }
                else       -> false
            }
            
            NEQ  -> when (val l = visit(expr.left).fromRef()) {
                is Boolean -> when (val r = visit(expr.right).fromRef()) {
                    is Boolean -> l != r
                    else       -> true
                }
                is Byte    -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l != r
                    is Short  -> l != r
                    is Int    -> l != r
                    is Long   -> l != r
                    is Float  -> l != r
                    is Double -> l != r
                    else      -> true
                }
                is Short   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l != r
                    is Short  -> l != r
                    is Int    -> l != r
                    is Long   -> l != r
                    is Float  -> l != r
                    is Double -> l != r
                    else      -> true
                }
                is Int     -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l != r
                    is Short  -> l != r
                    is Int    -> l != r
                    is Long   -> l != r
                    is Float  -> l != r
                    is Double -> l != r
                    else      -> true
                }
                is Long    -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l != r
                    is Short  -> l != r
                    is Int    -> l != r
                    is Long   -> l != r
                    is Float  -> l != r
                    is Double -> l != r
                    else      -> true
                }
                is Float   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l != r
                    is Short  -> l != r
                    is Int    -> l != r
                    is Long   -> l != r
                    is Float  -> l != r
                    is Double -> l != r
                    else      -> true
                }
                is Double  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l != r
                    is Short  -> l != r
                    is Int    -> l != r
                    is Long   -> l != r
                    is Float  -> l != r
                    is Double -> l != r
                    else      -> true
                }
                is Char    -> when (val r = visit(expr.right).fromRef()) {
                    is Char -> l != r
                    else    -> true
                }
                is String  -> when (val r = visit(expr.right).fromRef()) {
                    is String -> l != r
                    else      -> true
                }
                else       -> true
            }
            
            LSS  -> when (val l = visit(expr.left).fromRef()) {
                is Byte   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l < r
                    is Short  -> l < r
                    is Int    -> l < r
                    is Long   -> l < r
                    is Float  -> l < r
                    is Double -> l < r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Short  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l < r
                    is Short  -> l < r
                    is Int    -> l < r
                    is Long   -> l < r
                    is Float  -> l < r
                    is Double -> l < r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Int    -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l < r
                    is Short  -> l < r
                    is Int    -> l < r
                    is Long   -> l < r
                    is Float  -> l < r
                    is Double -> l < r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Long   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l < r
                    is Short  -> l < r
                    is Int    -> l < r
                    is Long   -> l < r
                    is Float  -> l < r
                    is Double -> l < r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Float  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l < r
                    is Short  -> l < r
                    is Int    -> l < r
                    is Long   -> l < r
                    is Float  -> l < r
                    is Double -> l < r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Double -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l < r
                    is Short  -> l < r
                    is Int    -> l < r
                    is Long   -> l < r
                    is Float  -> l < r
                    is Double -> l < r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Char   -> when (val r = visit(expr.right).fromRef()) {
                    is Char -> l < r
                    else    -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is String -> when (val r = visit(expr.right).fromRef()) {
                    is String -> l < r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                else      -> KBError.forScript("Left operand '$l' is invalid for binary '${expr.op}' operator!",
                    expr.left.loc)
            }
            
            LEQ  -> when (val l = visit(expr.left).fromRef()) {
                is Byte   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l <= r
                    is Short  -> l <= r
                    is Int    -> l <= r
                    is Long   -> l <= r
                    is Float  -> l <= r
                    is Double -> l <= r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Short  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l <= r
                    is Short  -> l <= r
                    is Int    -> l <= r
                    is Long   -> l <= r
                    is Float  -> l <= r
                    is Double -> l <= r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Int    -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l <= r
                    is Short  -> l <= r
                    is Int    -> l <= r
                    is Long   -> l <= r
                    is Float  -> l <= r
                    is Double -> l <= r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Long   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l <= r
                    is Short  -> l <= r
                    is Int    -> l <= r
                    is Long   -> l <= r
                    is Float  -> l <= r
                    is Double -> l <= r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Float  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l <= r
                    is Short  -> l <= r
                    is Int    -> l <= r
                    is Long   -> l <= r
                    is Float  -> l <= r
                    is Double -> l <= r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Double -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l <= r
                    is Short  -> l <= r
                    is Int    -> l <= r
                    is Long   -> l <= r
                    is Float  -> l <= r
                    is Double -> l <= r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Char   -> when (val r = visit(expr.right).fromRef()) {
                    is Char -> l <= r
                    else    -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is String -> when (val r = visit(expr.right).fromRef()) {
                    is String -> l <= r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                else      -> KBError.forScript("Left operand '$l' is invalid for binary '${expr.op}' operator!",
                    expr.left.loc)
            }
            
            GRT  -> when (val l = visit(expr.left).fromRef()) {
                is Byte   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l > r
                    is Short  -> l > r
                    is Int    -> l > r
                    is Long   -> l > r
                    is Float  -> l > r
                    is Double -> l > r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Short  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l > r
                    is Short  -> l > r
                    is Int    -> l > r
                    is Long   -> l > r
                    is Float  -> l > r
                    is Double -> l > r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Int    -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l > r
                    is Short  -> l > r
                    is Int    -> l > r
                    is Long   -> l > r
                    is Float  -> l > r
                    is Double -> l > r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Long   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l > r
                    is Short  -> l > r
                    is Int    -> l > r
                    is Long   -> l > r
                    is Float  -> l > r
                    is Double -> l > r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Float  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l > r
                    is Short  -> l > r
                    is Int    -> l > r
                    is Long   -> l > r
                    is Float  -> l > r
                    is Double -> l > r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Double -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l > r
                    is Short  -> l > r
                    is Int    -> l > r
                    is Long   -> l > r
                    is Float  -> l > r
                    is Double -> l > r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Char   -> when (val r = visit(expr.right).fromRef()) {
                    is Char -> l > r
                    else    -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is String -> when (val r = visit(expr.right).fromRef()) {
                    is String -> l > r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                else      -> KBError.forScript("Left operand '$l' is invalid for binary '${expr.op}' operator!",
                    expr.left.loc)
            }
            
            GEQ  -> when (val l = visit(expr.left).fromRef()) {
                is Byte   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l >= r
                    is Short  -> l >= r
                    is Int    -> l >= r
                    is Long   -> l >= r
                    is Float  -> l >= r
                    is Double -> l >= r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Short  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l >= r
                    is Short  -> l >= r
                    is Int    -> l >= r
                    is Long   -> l >= r
                    is Float  -> l >= r
                    is Double -> l >= r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Int    -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l >= r
                    is Short  -> l >= r
                    is Int    -> l >= r
                    is Long   -> l >= r
                    is Float  -> l >= r
                    is Double -> l >= r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Long   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l >= r
                    is Short  -> l >= r
                    is Int    -> l >= r
                    is Long   -> l >= r
                    is Float  -> l >= r
                    is Double -> l >= r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Float  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l >= r
                    is Short  -> l >= r
                    is Int    -> l >= r
                    is Long   -> l >= r
                    is Float  -> l >= r
                    is Double -> l >= r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Double -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l >= r
                    is Short  -> l >= r
                    is Int    -> l >= r
                    is Long   -> l >= r
                    is Float  -> l >= r
                    is Double -> l >= r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is Char   -> when (val r = visit(expr.right).fromRef()) {
                    is Char -> l >= r
                    else    -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                is String -> when (val r = visit(expr.right).fromRef()) {
                    is String -> l >= r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                else      -> KBError.forScript("Left operand '$l' is invalid for binary '${expr.op}' operator!",
                    expr.left.loc)
            }
            
            ISS  -> {
                val value = visit(expr.left).fromRef()
                
                val type = visit(expr.right).fromRef() as DataType
                
                type.matches(this, value) != null
            }
            
            ADD  -> when (val l = visit(expr.left).fromRef()) {
                is Byte   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l + r
                    is Short  -> l + r
                    is Int    -> l + r
                    is Long   -> l + r
                    is Float  -> l + r
                    is Double -> l + r
                    is Char   -> l.toString() + r
                    is String -> l.toString() + r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Short  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l + r
                    is Short  -> l + r
                    is Int    -> l + r
                    is Long   -> l + r
                    is Float  -> l + r
                    is Double -> l + r
                    is Char   -> l.toString() + r
                    is String -> l.toString() + r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Int    -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l + r
                    is Short  -> l + r
                    is Int    -> l + r
                    is Long   -> l + r
                    is Float  -> l + r
                    is Double -> l + r
                    is Char   -> l.toString() + r
                    is String -> l.toString() + r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Long   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l + r
                    is Short  -> l + r
                    is Int    -> l + r
                    is Long   -> l + r
                    is Float  -> l + r
                    is Double -> l + r
                    is Char   -> l.toString() + r
                    is String -> l.toString() + r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Float  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l + r
                    is Short  -> l + r
                    is Int    -> l + r
                    is Long   -> l + r
                    is Float  -> l + r
                    is Double -> l + r
                    is Char   -> l.toString() + r
                    is String -> l.toString() + r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Double -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l + r
                    is Short  -> l + r
                    is Int    -> l + r
                    is Long   -> l + r
                    is Float  -> l + r
                    is Double -> l + r
                    is Char   -> l.toString() + r
                    is String -> l.toString() + r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Char   -> when (val r = visit(expr.right).fromRef()) {
                    is Int    -> l + r
                    is Char   -> l.toString() + r.toString()
                    is String -> l + r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is String -> l + visit(expr.right).fromRef()
                
                else      -> KBError.forScript("Left operand '$l' is invalid for binary '${expr.op}' operator!",
                    expr.left.loc)
            }
            
            SUB  -> when (val l = visit(expr.left).fromRef()) {
                is Byte   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l - r
                    is Short  -> l - r
                    is Int    -> l - r
                    is Long   -> l - r
                    is Float  -> l - r
                    is Double -> l - r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Short  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l - r
                    is Short  -> l - r
                    is Int    -> l - r
                    is Long   -> l - r
                    is Float  -> l - r
                    is Double -> l - r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Int    -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l - r
                    is Short  -> l - r
                    is Int    -> l - r
                    is Long   -> l - r
                    is Float  -> l - r
                    is Double -> l - r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Long   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l - r
                    is Short  -> l - r
                    is Int    -> l - r
                    is Long   -> l - r
                    is Float  -> l - r
                    is Double -> l - r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Float  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l - r
                    is Short  -> l - r
                    is Int    -> l - r
                    is Long   -> l - r
                    is Float  -> l - r
                    is Double -> l - r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Double -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l - r
                    is Short  -> l - r
                    is Int    -> l - r
                    is Long   -> l - r
                    is Float  -> l - r
                    is Double -> l - r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                else      -> KBError.forScript("Left operand '$l' is invalid for binary '${expr.op}' operator!",
                    expr.left.loc)
            }
            
            MUL  -> when (val l = visit(expr.left).fromRef()) {
                is Byte   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l * r
                    is Short  -> l * r
                    is Int    -> l * r
                    is Long   -> l * r
                    is Float  -> l * r
                    is Double -> l * r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Short  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l * r
                    is Short  -> l * r
                    is Int    -> l * r
                    is Long   -> l * r
                    is Float  -> l * r
                    is Double -> l * r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Int    -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l * r
                    is Short  -> l * r
                    is Int    -> l * r
                    is Long   -> l * r
                    is Float  -> l * r
                    is Double -> l * r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Long   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l * r
                    is Short  -> l * r
                    is Int    -> l * r
                    is Long   -> l * r
                    is Float  -> l * r
                    is Double -> l * r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Float  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l * r
                    is Short  -> l * r
                    is Int    -> l * r
                    is Long   -> l * r
                    is Float  -> l * r
                    is Double -> l * r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Double -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l * r
                    is Short  -> l * r
                    is Int    -> l * r
                    is Long   -> l * r
                    is Float  -> l * r
                    is Double -> l * r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                else      -> KBError.forScript("Left operand '$l' is invalid for binary '${expr.op}' operator!",
                    expr.left.loc)
            }
            
            DIV  -> when (val l = visit(expr.left).fromRef()) {
                is Byte   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l / r
                    is Short  -> l / r
                    is Int    -> l / r
                    is Long   -> l / r
                    is Float  -> l / r
                    is Double -> l / r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Short  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l / r
                    is Short  -> l / r
                    is Int    -> l / r
                    is Long   -> l / r
                    is Float  -> l / r
                    is Double -> l / r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Int    -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l / r
                    is Short  -> l / r
                    is Int    -> l / r
                    is Long   -> l / r
                    is Float  -> l / r
                    is Double -> l / r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Long   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l / r
                    is Short  -> l / r
                    is Int    -> l / r
                    is Long   -> l / r
                    is Float  -> l / r
                    is Double -> l / r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Float  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l / r
                    is Short  -> l / r
                    is Int    -> l / r
                    is Long   -> l / r
                    is Float  -> l / r
                    is Double -> l / r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Double -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l / r
                    is Short  -> l / r
                    is Int    -> l / r
                    is Long   -> l / r
                    is Float  -> l / r
                    is Double -> l / r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                else      -> KBError.forScript("Left operand '$l' is invalid for binary '${expr.op}' operator!",
                    expr.left.loc)
            }
            
            REM  -> when (val l = visit(expr.left).fromRef()) {
                is Byte   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l % r
                    is Short  -> l % r
                    is Int    -> l % r
                    is Long   -> l % r
                    is Float  -> l % r
                    is Double -> l % r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Short  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l % r
                    is Short  -> l % r
                    is Int    -> l % r
                    is Long   -> l % r
                    is Float  -> l % r
                    is Double -> l % r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Int    -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l % r
                    is Short  -> l % r
                    is Int    -> l % r
                    is Long   -> l % r
                    is Float  -> l % r
                    is Double -> l % r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Long   -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l % r
                    is Short  -> l % r
                    is Int    -> l % r
                    is Long   -> l % r
                    is Float  -> l % r
                    is Double -> l % r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Float  -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l % r
                    is Short  -> l % r
                    is Int    -> l % r
                    is Long   -> l % r
                    is Float  -> l % r
                    is Double -> l % r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                is Double -> when (val r = visit(expr.right).fromRef()) {
                    is Byte   -> l % r
                    is Short  -> l % r
                    is Int    -> l % r
                    is Long   -> l % r
                    is Float  -> l % r
                    is Double -> l % r
                    else      -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                else      -> KBError.forScript("Left operand '$l' is invalid for binary '${expr.op}' operator!",
                    expr.left.loc)
            }
            
            AAS  -> {
                val value = visit(expr.left).fromRef()
                
                val type = visit(expr.right).fromRef() as DataType
                
                type.cast(this, value)
                    ?: KBError.forScript("Value '$value' cannot be cast to type '$type'!", expr.loc)
            }
            
            DOT  -> when (val l = visit(expr.left).fromRef()) {
                is DataInstance -> when (val r = expr.right) {
                    is Expr.Name -> l[r]
                    
                    else         -> KBError.forScript("Right operand '$r' is invalid for binary '${expr.op}' operator!",
                        expr.right.loc)
                }
                
                else            -> KBError.forScript("Left operand '$l' is invalid for binary '${expr.op}' operator!",
                    expr.left.loc)
            }
            
            else -> KBError.forScript("", expr.loc)
        }
    }
    
    override fun visitGetExpr(expr: Expr.Get): Any {
        return when (val target = visit(expr.target).fromRef()) {
            is String        -> when (val index = visit(expr.index).fromRef()) {
                is Int -> target[index]
                
                else   -> KBError.forScript("Value '$target' cannot be indexed with the value '$index'!",
                    expr.index.loc)
            }
            
            is ArrayInstance -> when (val index = visit(expr.index).fromRef()) {
                is Int -> target[index]
                
                else   -> KBError.forScript("Value '$target' cannot be indexed with the value '$index'!",
                    expr.index.loc)
            }
            
            is DataInstance  -> when (val index = visit(expr.index).fromRef()) {
                is String -> target[index]
                
                else      -> KBError.forScript("Value '$target' cannot be indexed with the value '$index'!",
                    expr.index.loc)
            }
            
            else             -> KBError.forScript("Value '$target' cannot be indexed!", expr.target.loc)
        }
    }
    
    override fun visitSetExpr(expr: Expr.Set): Any {
        return when (val target = visit(expr.target).fromRef()) {
            is ArrayInstance -> when (val index = visit(expr.index).fromRef()) {
                is Int -> {
                    val subExpr = visit(expr.expr).fromRef()
                    
                    if (target.type.matches(this, subExpr) != null) {
                        target[index] = subExpr
                    }
                    else {
                        KBError.forScript("Assigned value '$subExpr' (${DataType.infer(this, subExpr)}) doesn't match array type '${target.type}'!", expr.loc)
                    }
                }
                
                else   -> KBError.forScript("Value '$target' cannot be indexed with the value '$index'!",
                    expr.index.loc)
            }
            
            is DataInstance  -> when (val index = visit(expr.index).fromRef()) {
                is String -> target[index]
                
                else      -> KBError.forScript("Value '$target' cannot be indexed with the value '$index'!",
                    expr.index.loc)
            }
            
            else             -> KBError.forScript("Value '$target' cannot be indexed!", expr.target.loc)
        }
    }
    
    override fun visitInvokeExpr(expr: Expr.Invoke): Any {
        val subs = memory.getSubs(expr.name) ?: KBError.forScript("Sub name '${expr.name}' is undeclared!", expr.name.loc)
        
        val args = expr.args.map { visit(it).toExpr(it.loc) }
        
        val positionValid = mutableListOf<Pair<Stmt.Sub, List<Stmt.Decl>>>()
        
        for (sub in subs) {
            val resolved = resolvePosition(sub.params, args) ?: continue
            
            positionValid += sub to resolved
        }
        
        if (positionValid.isEmpty()) {
            KBError.forScript("Can't resolve argument positions (${expr.name})!", expr.loc)
        }
        
        val typeValid = mutableListOf<Pair<Stmt.Sub, List<Stmt.Decl>>>()
        
        for ((sub, decls) in positionValid) {
            val resolved = resolveType(decls) ?: continue
            
            typeValid += sub to resolved
        }
        
        if (typeValid.isEmpty()) {
            KBError.forScript("Can't resolve argument types (${expr.name})!", expr.loc)
        }
        
        val (sub, decls) = when (typeValid.size) {
            1    -> typeValid[0]
            
            else -> typeValid.minByOrNull { (_, decls) -> decls.size }!!
        }
        
        val type = visit(sub.type).fromRef() as DataType
        
        val scope = Memory.Scope(sub.name.value, global)
        
        try {
            memory.push(scope)
            
            for (decl in decls) {
                visit(decl)
            }
            
            if (sub.body.stmts.isNotEmpty()) {
                try {
                    visit(sub.body)
                }
                catch (r: Redirect.Yield) {
                    return type.matches(this, r.value)
                        ?: KBError.forScript("Function '${sub.name}' must yield a value of type '$type'!", r.origin)
                }
            }
            else {
                val subArgs = mutableListOf<Any>()
                
                for (name in decls.map { it.name }) {
                    subArgs += memory.getRef(name)!!.fromRef()
                }
                
                val builtin = library[sub] ?: KBError.forScript("No builtin found: '${sub.name}'!", sub.loc)
                
                val result = builtin(this, subArgs)
                
                return type.matches(this, result)
                    ?: KBError.forScript(
                        "Function '${sub.name}' must yield a value of type '$type'!",
                        Location.none
                    )
            }
        }
        finally {
            memory.pop()
        }
        
        if (type.matches(this, Unit) == null) {
            KBError.forScript("Function '${sub.name}' must yield a value!", expr.name.loc)
        }
        
        return Unit
    }
    
    private fun resolvePosition(params: List<Stmt.Decl>, args: List<Expr>): List<Stmt.Decl>? {
        val isVararg = params.isNotEmpty() && params.last().isVararg
        
        if (args.size > params.size && !isVararg) {
            return null
        }
        
        val exprs = MutableList<Expr>(params.size) { Expr.None }
        
        var p = 0
        
        for (i in exprs.indices) {
            if (p in args.indices) {
                if (!params[i].isVararg) {
                    exprs[i] = args[p++]
                }
            }
        }
        
        if (p in args.indices) {
            val varargs = mutableListOf<Expr>()
            
            while (p < args.size) {
                val arg = args[p++]
                
                varargs += arg/*if (arg.spread) {
                    Expr.Spread(arg.loc, arg.expr)
                }
                else {
                    arg.expr
                }*/
            }
            
            exprs[exprs.lastIndex] = Expr.Array(varargs[0].loc, varargs)
        }
        
        if (isVararg && exprs.last() is Expr.None) {
            exprs[exprs.lastIndex] = Expr.Array(Location.none, emptyList())
        }
        
        return if (exprs.all { it !is Expr.None })
            params.mapIndexed { i, decl -> decl.with(exprs[i]) }
        else
            null
    }
    
    private fun resolveType(params: List<Stmt.Decl>): List<Stmt.Decl>? {
        val exprs = params.map { it.expr }.toMutableList()
        
        for (i in params.indices) {
            val decl = params[i]
            
            val value = visit(decl.expr).fromRef()
            
            val finalValue = decl.type.value.matches(this, value) ?: return null
            
            exprs[i] = finalValue.toExpr()
        }
        
        return params.mapIndexed { i, decl -> decl.with(exprs[i]) }
    }
    
    fun instantiate(name: String, vararg elements: Any): DataInstance {
        val instantiate = Expr.Instantiate(Location.none, name.toName(), elements.map { it.toExpr() }.toList())
        
        return visit(instantiate) as DataInstance
    }
    
    override fun visitInstantiateExpr(expr: Expr.Instantiate): Any {
        val data = visit(expr.target).fromRef()
        
        data as? Stmt.Data ?: KBError.forScript("Value '$data' cannot be instantiated!", expr.target.loc)
        
        val scope = Memory.Scope(data.name.value, memory.peek())
        
        try {
            memory.push(scope)
            
            for ((i, decl) in data.decls.withIndex()) {
                visit(decl)
                
                if (i in expr.elements.indices) {
                    val assign = Expr.Binary(expr.elements[i].loc, ASN, decl.name, expr.elements[i])
                    
                    visit(assign)
                }
            }
        }
        finally {
            memory.pop()
        }
        
        return DataInstance(data.name, scope)
    }
}