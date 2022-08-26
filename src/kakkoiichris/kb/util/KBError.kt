package kakkoiichris.kb.util

import kakkoiichris.kb.lexer.Location
import kakkoiichris.kb.lexer.Token.Type
import kakkoiichris.kb.lexer.Token.Type.*
import kakkoiichris.kb.parser.Expr
import kakkoiichris.kb.parser.Stmt
import kakkoiichris.kb.script.DataType

class KBError(stage: String, message: String, location: Location) : RuntimeException("""Kakkoii Basic $stage Error: $message!$location""") {
    companion object {
        fun failure(message: String, location: Location = Location.none): Nothing =
            throw KBError("General", message, location)
        
        private fun forLexer(message: String, location: Location): Nothing =
            throw KBError("Lexer", message, location)
        
        fun illegalCharacter(illegal: Char, location: Location): Nothing =
            forLexer("Character '$illegal' is illegal", location)
        
        fun illegalUnicodeEscapeDigit(illegal: Char, location: Location): Nothing =
            forLexer("Character '$illegal' is not a hexadecimal digit", location)
        
        fun invalidCharacter(invalid: Char, expected: Char, location: Location): Nothing =
            forLexer("Character '$invalid' is invalid; expected '$expected'", location)
        
        fun invalidEscapeCharacter(invalid: Char, location: Location): Nothing =
            forLexer("Character escape '\\$invalid' is invalid", location)
        
        fun invalidSequence(invalid: String, expected: String, location: Location): Nothing =
            forLexer("Character sequence '$invalid' is invalid; expected '$expected!'", location)
        
        private fun forParser(message: String, location: Location): Nothing =
            throw KBError("Parser", message, location)
        
        fun duplicateElseCase(location: Location): Nothing =
            forParser("Switch statement may only have one else case", location)
        
        fun invalidDataType(type: Type, location: Location): Nothing =
            forParser("Data type beginning with type '$type' is invalid", location)
        
        fun invalidForLoop(location: Location): Nothing =
            forParser("For loop is invalid; expected '$EACH', '$TO', or '$IN'", location)
        
        fun invalidInstantiationTarget(location: Location): Nothing =
            forParser("Instantiate left operand must be a variable name", location)
        
        fun invalidInvocationTarget(location: Location): Nothing =
            forParser("Invoke left operand must be a variable name", location)
        
        fun invalidPipelineOperand(location: Location): Nothing =
            forParser("Right side of pipeline operator must be a function name or invocation", location)
        
        fun invalidTerminal(type: Type, location: Location): Nothing =
            forParser("Terminal expression beginning with type '$type' is invalid", location)
        
        fun invalidTokenType(invalid: Type, expected: Type, location: Location): Nothing =
            forParser("Token type '$invalid' is invalid; expected '$expected'", location)
        
        private fun forScript(message: String, location: Location): Nothing =
            throw KBError("Script", message, location)
        
        fun alreadyDeclaredVariable(name: Expr.Name, location: Location): Nothing =
            forScript("Variable '$name' has already been declared in this scope", location)
        
        fun alreadyDeclaredData(name: Expr.Name, location: Location): Nothing =
            forScript("Data '$name' has already been declared in this scope", location)
        
        fun alreadyDeclaredSub(signature: String, location: Location): Nothing =
            forScript("Sub '$signature' has already been declared in this scope", location)
        
        fun undeclaredVariable(name: Expr.Name, location: Location): Nothing =
            forScript("Variable '$name' has not been declared in this scope", location)
        
        fun undeclaredData(name: Expr.Name, location: Location): Nothing =
            forScript("Data '$name' has not been declared in this scope", location)
        
        fun undeclaredSub(name: Expr.Name, location: Location): Nothing =
            forScript("Sub '$name' has not been declared in this scope", location)
        
        fun emptyInstantiationTarget(location: Location): Nothing =
            forScript("Cannot infer data for instantiation", location)
        
        fun assignedNone(location: Location): Nothing =
            forScript("Cannot assign none to a variable", location)
        
        fun assignedToValue(location: Location): Nothing =
            forScript("Cannot assign to a value", location)
        
        fun reassignedConstant(location: Location): Nothing =
            forScript("Constant cannot be reassigned", location)
        
        fun inferEmpty(location: Location): Nothing =
            forScript("Data type of value 'empty' cannot be inferred", location)
        
        fun mismatchedType(value: Any, type: DataType, location: Location): Nothing =
            forScript("Assigned value '$value' is not of type '$type'", location)
        
        fun mismatchedReturnType(name: Expr.Name, type: DataType, location: Location): Nothing =
            forScript("Sub '$name' must yield a value of type '$type'", location)
        
        fun mismatchedBuiltinType(name: Expr.Name, type: DataType, location: Location): Nothing =
            forScript("Sub builtin '$name' must yield a value of type '$type'", location)
        
        fun mismatchedArraySize(location: Location): Nothing =
            forScript("Assigned array size does not match declared array size", location)
        
        fun noDefaultValue(type: DataType, location: Location): Nothing =
            forScript("No default value for data type '$type'", location)
        
        fun nonIndexedType(type: DataType, location: Location): Nothing =
            forScript("Value of type '$type' cannot be indexed", location)
        
        fun nonAccessedType(type: DataType, location: Location): Nothing =
            forScript("Value of type '$type' cannot be accessed", location)
        
        fun nonIterableType(type: DataType, location: Location): Nothing =
            forScript("Value of type '$type' is not iterable", location)
        
        fun nonPartitionedType(type: DataType, location: Location): Nothing =
            forScript("Value of type '$type' cannot be partitioned", location)
        
        fun noBuiltin(name: Expr.Name, location: Location): Nothing =
            forScript("No builtin found for sub '$name'", location)
        
        fun noMember(name: Expr.Name, member: String, location: Location): Nothing =
            forScript("Instance of data '$name' has no member called '$member'", location)
        
        fun noScope(location: Location): Nothing =
            forScript("There is no active scope", location)
        
        fun noYield(name: Expr.Name, location: Location): Nothing =
            forScript("Sub '$name' must yield a value", location)
        
        fun invalidUnaryOperand(operand: Any, operator: Expr.Unary.Operator, location: Location): Nothing =
            forScript("Operand '$operand' is invalid for unary '$operator' operator", location)
        
        fun invalidLeftOperand(operand: Any, operator: Expr.Binary.Operator, location: Location): Nothing =
            forScript("Left operand '$operand' is invalid for binary '$operator' operator", location)
        
        fun invalidRightOperand(operand: Any, operator: Expr.Binary.Operator, location: Location): Nothing =
            forScript("Right operand '$operand' is invalid for binary '$operator' operator", location)
        
        fun invalidTestExpression(value: Any, location: Location): Nothing =
            forScript("Test expression '$value' is invalid; must be a bool", location)
        
        fun invalidCast(value: Any, type: DataType, location: Location): Nothing =
            forScript("Value '$value' cannot be cast to type '$type'", location)
        
        fun invalidIndex(type: DataType, indexType: DataType, location: Location): Nothing =
            forScript("Value of type '$type' cannot be indexed by a value of type '$indexType'", location)
        
        fun invalidArraySize(): Nothing =
            forScript("Array init size must be of type int or smaller", Location.none)
        
        fun unresolvedPositions(name: Expr.Name, location: Location): Nothing =
            forScript("Can't resolve argument positions for sub '$name'", location)
        
        fun unresolvedTypes(name: Expr.Name, location: Location): Nothing =
            forScript("Can't resolve argument types for sub '$name'", location)
    }
    
    private val trace = Stack<Stmt>()
    
    val kbStackTrace: String
        get() {
            var result = ""
            
            while (!trace.isEmpty) {
                val stmt = trace.pop()!!
                
                result = "\t${stmt.trace}\n$result"
            }
            
            return "$message\n$result"
        }
    
    fun push(stmt: Stmt) {
        trace.push(stmt)
    }
}