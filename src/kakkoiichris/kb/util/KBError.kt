package kakkoiichris.kb.util

import kakkoiichris.kb.lexer.Context
import kakkoiichris.kb.lexer.Token.Keyword.*
import kakkoiichris.kb.lexer.Token.Type
import kakkoiichris.kb.parser.Expr
import kakkoiichris.kb.parser.Stmt
import kakkoiichris.kb.runtime.DataType
import kotlin.math.abs

class KBError(
    stage: String,
    subMessage: String,
    private val context: Context
) : RuntimeException("""KakkoiiBasic $stage Error: $subMessage!${context.location}""") {
    companion object {
        fun failure(message: String, context: Context = Context.none): Nothing =
            throw KBError("General", message, context)

        private fun forLexer(message: String, context: Context): Nothing =
            throw KBError("Lexer", message, context)

        fun illegalCharacter(illegal: Char, context: Context): Nothing =
            forLexer("Character '$illegal' is illegal", context)

        fun illegalUnicodeEscapeDigit(illegal: Char, context: Context): Nothing =
            forLexer("Character '$illegal' is not a hexadecimal digit", context)

        fun invalidCharacter(invalid: Char, expected: Char, context: Context): Nothing =
            forLexer("Character '$invalid' is invalid; expected '$expected'", context)

        fun invalidEscapeCharacter(invalid: Char, context: Context): Nothing =
            forLexer("Character escape '\\$invalid' is invalid", context)

        fun invalidSequence(invalid: String, expected: String, context: Context): Nothing =
            forLexer("Character sequence '$invalid' is invalid; expected '$expected!'", context)

        private fun forParser(message: String, context: Context): Nothing =
            throw KBError("Parser", message, context)

        fun duplicateElseCase(context: Context): Nothing =
            forParser("Switch statement may only have one else case", context)

        fun invalidDataType(type: Type, context: Context): Nothing =
            forParser("Data type beginning with type '$type' is invalid", context)

        fun invalidForLoop(context: Context): Nothing =
            forParser("For loop is invalid; expected '$EACH', '$TO', or '$IN'", context)

        fun invalidInstantiationTarget(context: Context): Nothing =
            forParser("Instantiate left operand must be a variable name", context)

        fun invalidInvocationTarget(context: Context): Nothing =
            forParser("Invoke left operand must be a variable name", context)

        fun invalidEntryTarget(context: Context): Nothing =
            forParser("Entry left operand must be a variable name", context)

        fun invalidPipelineOperand(context: Context): Nothing =
            forParser("Right side of pipeline operator must be a function name or invocation", context)

        fun invalidTerminal(type: Type, context: Context): Nothing =
            forParser("Terminal expression beginning with type '$type' is invalid", context)

        fun invalidTokenType(invalid: Type, expected: Type, context: Context): Nothing =
            forParser("Token type '$invalid' is invalid; expected '$expected'", context)

        fun nonLabeledStatement(type: Type, context: Context): Nothing =
            forParser("Statement beginning with '$type' cannot have a label", context)

        private fun forScript(message: String, context: Context): Nothing =
            throw KBError("Script", message, context)

        fun alreadyDeclaredVariable(name: Expr.Name, context: Context): Nothing =
            forScript("Variable '$name' has already been declared in this scope", context)

        fun redeclaredData(name: Expr.Name, context: Context): Nothing =
            forScript("Data '$name' has already been declared in this scope", context)

        fun redeclaredSub(signature: String, context: Context): Nothing =
            forScript("Sub '$signature' has already been declared in this scope", context)

        fun redeclaredAlias(name: Expr.Name, context: Context): Nothing =
            forScript("Type alias '$name' has already been declared in this scope", context)

        fun undeclaredVariable(name: Expr.Name, context: Context): Nothing =
            forScript("Variable '$name' has not been declared in this scope", context)

        fun undeclaredData(name: Expr.Name, context: Context): Nothing =
            forScript("Data '$name' has not been declared in this scope", context)

        fun undeclaredSub(name: Expr.Name, context: Context): Nothing =
            forScript("Sub '$name' has not been declared in this scope", context)

        fun emptyInstantiationTarget(context: Context): Nothing =
            forScript("Cannot infer data for instantiation", context)

        fun assignedNone(context: Context): Nothing =
            forScript("Cannot assign none to a variable", context)

        fun assignedToValue(context: Context): Nothing =
            forScript("Cannot assign to a value", context)

        fun reassignedConstant(context: Context): Nothing =
            forScript("Constant cannot be reassigned", context)

        fun inferEmpty(context: Context): Nothing =
            forScript("Data type of value 'empty' cannot be inferred", context)

        fun mismatchedType(value: Any, type: DataType, context: Context): Nothing =
            forScript("Assigned value '$value' is not of type '$type'", context)

        fun mismatchedReturnType(name: Expr.Name, type: DataType, context: Context): Nothing =
            forScript("Sub '$name' must yield a value of type '$type'", context)

        fun mismatchedBuiltinType(name: Expr.Name, type: DataType, context: Context): Nothing =
            forScript("Sub builtin '$name' must yield a value of type '$type'", context)

        fun mismatchedArraySize(context: Context): Nothing =
            forScript("Assigned array size does not match declared array size", context)

        fun noDefaultValue(type: DataType, context: Context): Nothing =
            forScript("No default value for data type '$type'", context)

        fun nonIndexedType(type: DataType, context: Context): Nothing =
            forScript("Value of type '$type' cannot be indexed", context)

        fun nonAccessedType(type: DataType, context: Context): Nothing =
            forScript("Value of type '$type' cannot be accessed", context)

        fun nonIterableType(type: DataType, context: Context): Nothing =
            forScript("Value of type '$type' is not iterable", context)

        fun nonPartitionedType(type: DataType, context: Context): Nothing =
            forScript("Value of type '$type' cannot be partitioned", context)

        fun noBuiltin(name: Expr.Name, context: Context): Nothing =
            forScript("No builtin found for sub '$name'", context)

        fun noMember(name: Expr.Name, member: String, context: Context): Nothing =
            forScript("Instance of data '$name' has no member called '$member'", context)

        fun noScope(context: Context): Nothing =
            forScript("There is no active scope", context)

        fun noYield(name: Expr.Name, context: Context): Nothing =
            forScript("Sub '$name' must yield a value", context)

        fun noSub(name: String): Nothing =
            forScript("Sub '$name' not found for the given arguments", Context.none)

        fun invalidUnaryOperand(operand: Any, operator: Expr.Unary.Operator, context: Context): Nothing =
            forScript("Operand '$operand' is invalid for unary '$operator' operator", context)

        fun invalidLeftOperand(operand: Any, operator: Expr.Binary.Operator, context: Context): Nothing =
            forScript("Left operand '$operand' is invalid for binary '$operator' operator", context)

        fun invalidRightOperand(operand: Any, operator: Expr.Binary.Operator, context: Context): Nothing =
            forScript("Right operand '$operand' is invalid for binary '$operator' operator", context)

        fun invalidTestExpression(value: Any, context: Context): Nothing =
            forScript("Test expression '$value' is invalid; must be a bool", context)

        fun invalidCast(value: Any, type: DataType, context: Context): Nothing =
            forScript("Value '$value' cannot be cast to type '$type'", context)

        fun invalidIndex(type: DataType, indexType: DataType, context: Context): Nothing =
            forScript("Value of type '$type' cannot be indexed by a value of type '$indexType'", context)

        fun invalidArraySize(): Nothing =
            forScript("Initial array size must be of type int or smaller", Context.none)

        fun unresolvedPositions(name: Expr.Name, context: Context): Nothing =
            forScript("Can't resolve argument positions for sub '$name'", context)

        fun unresolvedTypes(name: Expr.Name, context: Context): Nothing =
            forScript("Can't resolve argument types for sub '$name'", context)
    }

    private val trace = Stack<Stmt>()

    private val kbStackTrace: String
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

    override fun printStackTrace() {
        val text = buildString {
            appendLine(kbStackTrace)

            if (context !== Context.none) {
                appendLine(context.line)

                append(" ".repeat(context.region.first - 1))
                appendLine("^".repeat(abs(context.region.last - context.region.first)))
            }
        }

        System.err.println(text)
    }
}