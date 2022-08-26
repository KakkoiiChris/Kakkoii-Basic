@file:Suppress("RedundantUnitExpression")

package kakkoiichris.kb.script

import kakkoiichris.kb.parser.Stmt
import java.awt.Color
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.*
import kotlin.system.exitProcess

class StandardLibrary {
    private val builtins = mutableMapOf<String, Builtin>()
    
    private lateinit var window: Window
    
    init {
        addGeneral()
        
        addChar()
        
        addString()
        
        addMath()
        
        addGraphics()
    }
    
    private fun addGeneral() {
        add("print", DataType.Primitive.NONE, DataType.Primitive.ANY.vararg) { script, args ->
            val (subArgs) = args
            
            subArgs as ArrayInstance
            
            for (arg in subArgs) {
                print(script.getString(arg))
            }
        }
        
        add("input", DataType.Primitive.STRING, DataType.Primitive.ANY.vararg) { script, args ->
            val (subArgs) = args
            
            subArgs as ArrayInstance
            
            for (arg in subArgs) {
                print(script.getString(arg))
            }
            
            readLine() ?: ""
        }
        
        add("read", DataType.Primitive.STRING, DataType.Primitive.STRING) { _, args ->
            val (path) = args
            
            path as String
            
            Files.readString(Paths.get(path))
        }
        
        add("write", DataType.Primitive.NONE, DataType.Primitive.STRING, DataType.Primitive.ANY) { _, args ->
            val (path, data) = args
            
            path as String
            
            Files.writeString(Paths.get(path), data.toString())
            
            Unit
        }
        
        add("concat", DataType.Primitive.STRING, DataType.Primitive.ANY.vararg) { script, args ->
            val (subArgs) = args
            
            subArgs as ArrayInstance
            
            buildString {
                for (arg in subArgs) {
                    append(script.getString(arg))
                }
            }
        }
        
        add("sleep", DataType.Primitive.NONE, DataType.Primitive.LONG) { _, args ->
            val (milliseconds) = args
            
            milliseconds as Long
            
            Thread.sleep(milliseconds)
        }
        
        add("milliseconds", DataType.Primitive.LONG) { _, _ -> System.currentTimeMillis() }
        
        add("nanoseconds", DataType.Primitive.LONG) { _, _ -> System.nanoTime() }
        
        add("typeof", DataType.Primitive.STRING, DataType.Primitive.ANY) { script, args ->
            val (x) = args
            
            DataType.infer(script, x).toString()
        }
        
        add("invoke", DataType.Primitive.ANY, DataType.Primitive.STRING, DataType.Primitive.ANY.vararg) { script, args ->
            val (name, arguments) = args
            
            name as String
            arguments as ArrayInstance
            
            script.invoke(name, *arguments.toTypedArray()) ?: TODO("INVOKE SUB NOT FOUND")
        }
        
        add("exit", DataType.Primitive.NONE, DataType.Primitive.INT) { _, args ->
            val (code) = args
            
            code as Int
            
            exitProcess(code)
        }
    }
    
    private fun addChar() {
        add("isAlpha", DataType.Primitive.BOOL, DataType.Primitive.CHAR) { _, args ->
            val (c) = args
            
            c as Char
            
            c.isLetter()
        }
        
        add("isAlnum", DataType.Primitive.BOOL, DataType.Primitive.CHAR) { _, args ->
            val (c) = args
            
            c as Char
            
            c.isLetterOrDigit()
        }
        
        add("isDigit", DataType.Primitive.BOOL, DataType.Primitive.CHAR) { _, args ->
            val (c) = args
            
            c as Char
            
            c.isDigit()
        }
        
        add("isSpace", DataType.Primitive.BOOL, DataType.Primitive.CHAR) { _, args ->
            val (c) = args
            
            c as Char
            
            c.isWhitespace()
        }
        
        add("isVowel", DataType.Primitive.BOOL, DataType.Primitive.CHAR) { _, args ->
            val (c) = args
            
            c as Char
            
            c in "AEIOUaeiou"
        }
        
        add("isLower", DataType.Primitive.BOOL, DataType.Primitive.CHAR) { _, args ->
            val (c) = args
            
            c as Char
            
            c.isLowerCase()
        }
        
        add("isUpper", DataType.Primitive.BOOL, DataType.Primitive.CHAR) { _, args ->
            val (c) = args
            
            c as Char
            
            c.isUpperCase()
        }
        
        add("toLower", DataType.Primitive.CHAR, DataType.Primitive.CHAR) { _, args ->
            val (c) = args
            
            c as Char
            
            c.lowercase()
        }
        
        add("toUpper", DataType.Primitive.CHAR, DataType.Primitive.CHAR) { _, args ->
            val (c) = args
            
            c as Char
            
            c.uppercase()
        }
    }
    
    private fun addString() {
        add("isBlank", DataType.Primitive.BOOL, DataType.Primitive.STRING) { _, args ->
            val (s) = args
            
            s as String
            
            s.isBlank()
        }
        
        add("isEmpty", DataType.Primitive.BOOL, DataType.Primitive.STRING) { _, args ->
            val (s) = args
            
            s as String
            
            s.isBlank()
        }
        
        add("toLower", DataType.Primitive.STRING, DataType.Primitive.STRING) { _, args ->
            val (s) = args
            
            s as String
            
            s.lowercase()
        }
        
        add("toUpper", DataType.Primitive.STRING, DataType.Primitive.STRING) { _, args ->
            val (s) = args
            
            s as String
            
            s.uppercase()
        }
        
        add(
            "startsWith",
            DataType.Primitive.BOOL,
            DataType.Primitive.STRING,
            DataType.Primitive.STRING,
            DataType.Primitive.BOOL
        ) { _, args ->
            val (s, prefix, ignoreCase) = args
            
            s as String
            prefix as String
            ignoreCase as Boolean
            
            s.startsWith(prefix, ignoreCase)
        }
        
        add(
            "endsWith",
            DataType.Primitive.BOOL,
            DataType.Primitive.STRING,
            DataType.Primitive.STRING,
            DataType.Primitive.BOOL
        ) { _, args ->
            val (s, suffix, ignoreCase) = args
            
            s as String
            suffix as String
            ignoreCase as Boolean
            
            s.endsWith(suffix, ignoreCase)
        }
        
        add("trim", DataType.Primitive.STRING, DataType.Primitive.STRING) { _, args ->
            val (s) = args
            
            s as String
            
            s.trim()
        }
        
        add("trimStart", DataType.Primitive.STRING, DataType.Primitive.STRING) { _, args ->
            val (s) = args
            
            s as String
            
            s.trimStart()
        }
        
        add("trimEnd", DataType.Primitive.STRING, DataType.Primitive.STRING) { _, args ->
            val (s) = args
            
            s as String
            
            s.trimEnd()
        }
        
        add(
            "substring",
            DataType.Primitive.STRING,
            DataType.Primitive.STRING,
            DataType.Primitive.INT,
            DataType.Primitive.INT
        ) { _, args ->
            val (s, startIndex, endIndex) = args
            
            s as String
            startIndex as Int
            endIndex as Int
            
            s.substring(startIndex, endIndex)
        }
        
        add(
            "split",
            DataType.Primitive.STRING.array,
            DataType.Primitive.STRING,
            DataType.Primitive.STRING
        ) { _, args ->
            
            val (s, regex) = args
            
            s as String
            regex as String
            
            s.split(regex.toRegex()).toTypedArray().toArrayInstance()
        }
        
        add("indexOf", DataType.Primitive.INT, DataType.Primitive.STRING, DataType.Primitive.CHAR) { _, args ->
            val (s, c) = args
            
            s as String
            c as Char
            
            s.indexOf(c)
        }
        
        add("indexOf", DataType.Primitive.INT, DataType.Primitive.STRING, DataType.Primitive.STRING) { _, args ->
            val (s, sequence) = args
            
            s as String
            sequence as String
            
            s.indexOf(sequence)
        }
        
        add("lastIndexOf", DataType.Primitive.INT, DataType.Primitive.STRING, DataType.Primitive.CHAR) { _, args ->
            val (s, c) = args
            
            s as String
            c as Char
            
            s.lastIndexOf(c)
        }
        
        add("lastIndexOf", DataType.Primitive.INT, DataType.Primitive.STRING, DataType.Primitive.STRING) { _, args ->
            val (s, sequence) = args
            
            s as String
            sequence as String
            
            s.lastIndexOf(sequence)
        }
        
        add(
            "padStart",
            DataType.Primitive.STRING,
            DataType.Primitive.STRING,
            DataType.Primitive.INT,
            DataType.Primitive.CHAR
        ) { _, args ->
            val (s, length, c) = args
            
            s as String
            length as Int
            c as Char
            
            s.padStart(length, c)
        }
        
        add(
            "padEnd",
            DataType.Primitive.STRING,
            DataType.Primitive.STRING,
            DataType.Primitive.INT,
            DataType.Primitive.CHAR
        ) { _, args ->
            val (s, length, c) = args
            
            s as String
            length as Int
            c as Char
            
            s.padEnd(length, c)
        }
        
        add(
            "replace",
            DataType.Primitive.STRING,
            DataType.Primitive.STRING,
            DataType.Primitive.CHAR,
            DataType.Primitive.CHAR
        ) { _, args ->
            val (s, old, new) = args
            
            s as String
            old as Char
            new as Char
            
            s.replace(old, new)
        }
        
        add(
            "replace",
            DataType.Primitive.STRING,
            DataType.Primitive.STRING,
            DataType.Primitive.STRING,
            DataType.Primitive.STRING
        ) { _, args ->
            val (s, old, new) = args
            
            s as String
            old as String
            new as String
            
            s.replace(old, new)
        }
    }
    
    private fun addMath() {
        add("abs", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            abs(n)
        }
        
        add("abs", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            abs(n)
        }
        
        add("abs", DataType.Primitive.INT, DataType.Primitive.INT) { _, args ->
            val (n) = args
            
            n as Int
            
            abs(n)
        }
        
        add("abs", DataType.Primitive.LONG, DataType.Primitive.LONG) { _, args ->
            val (n) = args
            
            n as Long
            
            abs(n)
        }
        
        add("acos", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            acos(n)
        }
        
        add("acos", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            acos(n)
        }
        
        add("acosh", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            acosh(n)
        }
        
        add("acosh", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            acosh(n)
        }
        
        add("asin", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            asin(n)
        }
        
        add("asin", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            asin(n)
        }
        
        add("asinh", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            asinh(n)
        }
        
        add("asinh", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            asinh(n)
        }
        
        add("atan", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            atan(n)
        }
        
        add("atan", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            atan(n)
        }
        
        add(
            "atan2",
            DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE
        ) { _, args ->
            val (y, x) = args
            
            y as Double
            x as Double
            
            atan2(y, x)
        }
        
        add("atan2", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (y, x) = args
            
            y as Float
            x as Float
            
            atan2(y, x)
        }
        
        add("atanh", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            atanh(n)
        }
        
        add("atanh", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            atanh(n)
        }
        
        add("ceil", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            ceil(n)
        }
        
        add("ceil", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            ceil(n)
        }
        
        add("cos", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            cos(n)
        }
        
        add("cos", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            cos(n)
        }
        
        add("cosh", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            cosh(n)
        }
        
        add("cosh", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            cosh(n)
        }
        
        add("exp", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            exp(n)
        }
        
        add("exp", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            exp(n)
        }
        
        add("expm1", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            expm1(n)
        }
        
        add("expm1", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            expm1(n)
        }
        
        add("floor", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            floor(n)
        }
        
        add("floor", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            floor(n)
        }
        
        add(
            "hypot",
            DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE
        ) { _, args ->
            val (x, y) = args
            
            x as Double
            y as Double
            
            hypot(x, y)
        }
        
        add("hypot", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (x, y) = args
            
            x as Float
            y as Float
            
            hypot(x, y)
        }
        
        add("IEEErem", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n, divisor) = args
            
            n as Double
            divisor as Double
            
            n.IEEErem(divisor)
        }
        
        add("IEEErem", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n, divisor) = args
            
            n as Float
            divisor as Float
            
            n.IEEErem(divisor)
        }
        
        add("ln", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            ln(n)
        }
        
        add("ln", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            ln(n)
        }
        
        add("ln1p", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            ln1p(n)
        }
        
        add("ln1p", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            ln1p(n)
        }
        
        add(
            "log",
            DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE
        ) { _, args ->
            val (n, base) = args
            
            n as Double
            base as Double
            
            log(n, base)
        }
        
        add("log", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n, base) = args
            
            n as Float
            base as Float
            
            log(n, base)
        }
        
        add("log10", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            log10(n)
        }
        
        add("log10", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            log10(n)
        }
        
        add("log2", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            log2(n)
        }
        
        add("log2", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            log2(n)
        }
        
        add("max", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (a, b) = args
            
            a as Double
            b as Double
            
            max(a, b)
        }
        
        add("max", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (a, b) = args
            
            a as Float
            b as Float
            
            max(a, b)
        }
        
        add("max", DataType.Primitive.INT, DataType.Primitive.INT, DataType.Primitive.INT) { _, args ->
            val (a, b) = args
            
            a as Int
            b as Int
            
            max(a, b)
        }
        
        add("max", DataType.Primitive.LONG, DataType.Primitive.LONG, DataType.Primitive.LONG) { _, args ->
            val (a, b) = args
            
            a as Long
            b as Long
            
            max(a, b)
        }
        
        add("min", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (a, b) = args
            
            a as Double
            b as Double
            
            min(a, b)
        }
        
        add("min", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (a, b) = args
            
            a as Float
            b as Float
            
            min(a, b)
        }
        
        add("min", DataType.Primitive.INT, DataType.Primitive.INT, DataType.Primitive.INT) { _, args ->
            val (a, b) = args
            
            a as Int
            b as Int
            
            min(a, b)
        }
        
        add("min", DataType.Primitive.LONG, DataType.Primitive.LONG, DataType.Primitive.LONG) { _, args ->
            val (a, b) = args
            
            a as Long
            b as Long
            
            min(a, b)
        }
        
        add("nextdown", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            n.nextDown()
        }
        
        add("nextdown", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            n.nextDown()
        }
        
        add(
            "nexttowards",
            DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE
        ) { _, args ->
            val (a, b) = args
            
            a as Double
            b as Double
            
            a.nextTowards(b)
        }
        
        add(
            "nexttowards",
            DataType.Primitive.FLOAT, DataType.Primitive.FLOAT, DataType.Primitive.FLOAT
        ) { _, args ->
            val (a, b) = args
            
            a as Float
            b as Float
            
            a.nextTowards(b)
        }
        
        add("nextup", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            n.nextUp()
        }
        
        add("nextup", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            n.nextUp()
        }
        
        add("pow", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (b, e) = args
            
            b as Double
            e as Double
            
            b.pow(e)
        }
        
        add("pow", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (b, e) = args
            
            b as Float
            e as Float
            
            b.pow(e)
        }
        
        add("pow", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE, DataType.Primitive.INT) { _, args ->
            val (b, e) = args
            
            b as Double
            e as Int
            
            b.pow(e)
        }
        
        add("pow", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT, DataType.Primitive.INT) { _, args ->
            val (b, e) = args
            
            b as Float
            e as Int
            
            b.pow(e)
        }
        
        add("random", DataType.Primitive.DOUBLE) { _, _ -> Math.random() }
        
        add("round", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            round(n)
        }
        
        add("round", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            round(n)
        }
        
        add("roundToInt", DataType.Primitive.INT, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            n.roundToInt()
        }
        
        add("roundToInt", DataType.Primitive.INT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            n.roundToInt()
        }
        
        add("roundToLong", DataType.Primitive.LONG, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            n.roundToLong()
        }
        
        add("roundToLong", DataType.Primitive.LONG, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            n.roundToLong()
        }
        
        add("sign", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            n.sign
        }
        
        add("sign", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            n.sign
        }
        
        add("sign", DataType.Primitive.INT, DataType.Primitive.INT) { _, args ->
            val (n) = args
            
            n as Int
            
            n.sign
        }
        
        add("sign", DataType.Primitive.INT, DataType.Primitive.LONG) { _, args ->
            val (n) = args
            
            n as Long
            
            n.sign
        }
        
        add("sin", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            sin(n)
        }
        
        add("sin", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            sin(n)
        }
        
        add("sinh", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            sinh(n)
        }
        
        add("sinh", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            sinh(n)
        }
        
        add("sqrt", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            sqrt(n)
        }
        
        add("sqrt", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            sqrt(n)
        }
        
        add("tan", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            tan(n)
        }
        
        add("tan", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            tan(n)
        }
        
        add("tanh", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            tanh(n)
        }
        
        add("tanh", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            tanh(n)
        }
        
        add("truncate", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            truncate(n)
        }
        
        add("truncate", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            truncate(n)
        }
        
        add("ulp", DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            n.ulp
        }
        
        add("ulp", DataType.Primitive.FLOAT, DataType.Primitive.FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            n.ulp
        }
        
        add(
            "map",
            DataType.Primitive.DOUBLE,
            DataType.Primitive.DOUBLE,
            DataType.Primitive.DOUBLE,
            DataType.Primitive.DOUBLE,
            DataType.Primitive.DOUBLE,
            DataType.Primitive.DOUBLE
        ) { _, args ->
            val (n, fromMin, fromMax, toMin, toMax) = args
            
            n as Double
            fromMin as Double
            fromMax as Double
            toMin as Double
            toMax as Double
            
            (n - fromMin) * (toMax - toMin) / (fromMax - fromMin) + toMin
        }
        
        add(
            "map",
            DataType.Primitive.FLOAT,
            DataType.Primitive.FLOAT,
            DataType.Primitive.FLOAT,
            DataType.Primitive.FLOAT,
            DataType.Primitive.FLOAT,
            DataType.Primitive.FLOAT
        ) { _, args ->
            val (n, fromMin, fromMax, toMin, toMax) = args
            
            n as Float
            fromMin as Float
            fromMax as Float
            toMin as Float
            toMax as Float
            
            (n - fromMin) * (toMax - toMin) / (fromMax - fromMin) + toMin
        }
        
        add(
            "map",
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT
        ) { _, args ->
            val (n, fromMin, fromMax, toMin, toMax) = args
            
            n as Int
            fromMin as Int
            fromMax as Int
            toMin as Int
            toMax as Int
            
            ((n - fromMin) * (toMax - toMin) / (fromMax - fromMin).toDouble() + toMin).toInt()
        }
        
        add(
            "map",
            DataType.Primitive.LONG,
            DataType.Primitive.LONG,
            DataType.Primitive.LONG,
            DataType.Primitive.LONG,
            DataType.Primitive.LONG,
            DataType.Primitive.LONG
        ) { _, args ->
            val (n, fromMin, fromMax, toMin, toMax) = args
            
            n as Long
            fromMin as Long
            fromMax as Long
            toMin as Long
            toMax as Long
            
            ((n - fromMin) * (toMax - toMin) / (fromMax - fromMin).toDouble() + toMin).toLong()
        }
    }
    
    private fun addGraphics() {
        add(
            "createWindow",
            DataType.Primitive.NONE,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.STRING
        ) { _, args ->
            val (width, height, title) = args
            
            width as Int
            height as Int
            title as String
            
            window = Window(width, height, title)
            
            Unit
        }
        
        add("openWindow", DataType.Primitive.NONE) { _, _ -> window.open() }
        
        add("closeWindow", DataType.Primitive.NONE) { _, _ -> window.close() }
        
        add("windowIsOpen", DataType.Primitive.BOOL) { _, _ -> window.isOpen }
        
        add("getColor", "Color".data) { script, _ ->
            val color = window.getColor()
            
            script.instantiate("Color", color.red, color.green, color.blue, color.alpha)
        }
        
        add(
            "setColor",
            DataType.Primitive.NONE,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT
        ) { _, args ->
            val (red, green, blue, alpha) = args
            
            red as Int
            green as Int
            blue as Int
            alpha as Int
            
            window.setColor(red, green, blue, alpha)
        }
        
        add(
            "hsbToColor",
            "Color".data,
            DataType.Primitive.DOUBLE,
            DataType.Primitive.DOUBLE,
            DataType.Primitive.DOUBLE
        ) { script, args ->
            val (h, s, b) = args
            
            h as Double
            s as Double
            b as Double
            
            val color = Color(Color.HSBtoRGB(h.toFloat(), s.toFloat(), b.toFloat()))
            
            script.instantiate("Color", color.red, color.green, color.blue, color.alpha)
        }
        
        add("getFont", "Font".data) { script, _ ->
            val font = window.getFont()
            
            script.instantiate("Font", font.name, font.style, font.size)
        }
        
        add(
            "setFont",
            DataType.Primitive.NONE,
            DataType.Primitive.STRING,
            DataType.Primitive.INT,
            DataType.Primitive.INT
        ) { _, args ->
            val (name, style, size) = args
            
            name as String
            style as Int
            size as Int
            
            window.setFont(name, style, size)
        }
        
        add("getStroke", "Font".data) { script, _ ->
            val stroke = window.getStroke()
            
            script.instantiate(
                "Stroke",
                stroke.lineWidth,
                stroke.endCap,
                stroke.lineJoin,
                stroke.miterLimit,
                stroke.dashArray.toArrayInstance(),
                stroke.dashPhase
            )
        }
        
        add(
            "setStroke",
            DataType.Primitive.NONE,
            DataType.Primitive.FLOAT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.FLOAT,
            DataType.Primitive.FLOAT.array,
            DataType.Primitive.FLOAT
        ) { _, args ->
            val (width, cap, join, miterLimit, dash, dashPhase) = args
            
            width as Float
            cap as Int
            join as Int
            miterLimit as Float
            dash as ArrayInstance
            dashPhase as Float
            
            val dashFA = dash.asFloatArray()
            
            window.setStroke(width, cap, join, miterLimit, dashFA, dashPhase)
        }
        
        add("translate", DataType.Primitive.NONE, DataType.Primitive.INT, DataType.Primitive.INT) { _, args ->
            val (x, y) = args
            
            x as Int
            y as Int
            
            window.translate(x, y)
        }
        
        add("translate", DataType.Primitive.NONE, DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (x, y) = args
            
            x as Double
            y as Double
            
            window.translate(x, y)
        }
        
        add("rotate", DataType.Primitive.NONE, DataType.Primitive.DOUBLE) { _, args ->
            val (theta) = args
            
            theta as Double
            
            window.rotate(theta)
        }
        
        add(
            "rotate",
            DataType.Primitive.NONE,
            DataType.Primitive.DOUBLE,
            DataType.Primitive.DOUBLE,
            DataType.Primitive.DOUBLE
        ) { _, args ->
            val (theta, x, y) = args
            
            theta as Double
            x as Double
            y as Double
            
            window.rotate(theta, x, y)
        }
        
        add("scale", DataType.Primitive.NONE, DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (x, y) = args
            
            x as Double
            y as Double
            
            window.scale(x, y)
        }
        
        add("shear", DataType.Primitive.NONE, DataType.Primitive.DOUBLE, DataType.Primitive.DOUBLE) { _, args ->
            val (x, y) = args
            
            x as Double
            y as Double
            
            window.shear(x, y)
        }
        
        add("pushMatrix", DataType.Primitive.NONE) { _, _ -> window.pushMatrix() }
        
        add("popMatrix", DataType.Primitive.NONE) { _, _ -> window.popMatrix() }
        
        add("clear", DataType.Primitive.NONE) { _, _ -> window.clear() }
        
        add(
            "drawLine",
            DataType.Primitive.NONE,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT
        ) { _, args ->
            val (xa, ya, xb, yb) = args
            
            xa as Int
            ya as Int
            xb as Int
            yb as Int
            
            window.drawLine(xa, ya, xb, yb)
        }
        
        add(
            "drawRect",
            DataType.Primitive.NONE,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT
        ) { _, args ->
            val (x, y, width, height) = args
            
            x as Int
            y as Int
            width as Int
            height as Int
            
            window.drawRect(x, y, width, height)
        }
        
        add(
            "fillRect",
            DataType.Primitive.NONE,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT
        ) { _, args ->
            val (x, y, width, height) = args
            
            x as Int
            y as Int
            width as Int
            height as Int
            
            window.fillRect(x, y, width, height)
        }
        
        add(
            "drawOval",
            DataType.Primitive.NONE,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT
        ) { _, args ->
            val (x, y, width, height) = args
            
            x as Int
            y as Int
            width as Int
            height as Int
            
            window.drawOval(x, y, width, height)
        }
        
        add(
            "fillOval",
            DataType.Primitive.NONE,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT
        ) { _, args ->
            val (x, y, width, height) = args
            
            x as Int
            y as Int
            width as Int
            height as Int
            
            window.fillOval(x, y, width, height)
        }
        
        add(
            "drawRoundRect",
            DataType.Primitive.NONE,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT
        ) { _, args ->
            val (x, y, width, height, arcWidth, arcHeight) = args
            
            x as Int
            y as Int
            width as Int
            height as Int
            arcWidth as Int
            arcHeight as Int
            
            window.drawRoundRect(x, y, width, height, arcWidth, arcHeight)
        }
        
        add(
            "fillRoundRect",
            DataType.Primitive.NONE,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT
        ) { _, args ->
            val (x, y, width, height, arcWidth, arcHeight) = args
            
            x as Int
            y as Int
            width as Int
            height as Int
            arcWidth as Int
            arcHeight as Int
            
            window.fillRoundRect(x, y, width, height, arcWidth, arcHeight)
        }
        
        add(
            "draw3DRect",
            DataType.Primitive.NONE,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.BOOL
        ) { _, args ->
            val (x, y, width, height, raised) = args
            
            x as Int
            y as Int
            width as Int
            height as Int
            raised as Boolean
            
            window.draw3DRect(x, y, width, height, raised)
        }
        
        add(
            "fill3DRect",
            DataType.Primitive.NONE,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.BOOL
        ) { _, args ->
            val (x, y, width, height, raised) = args
            
            x as Int
            y as Int
            width as Int
            height as Int
            raised as Boolean
            
            window.fill3DRect(x, y, width, height, raised)
        }
        
        add(
            "drawArc",
            DataType.Primitive.NONE,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT
        ) { _, args ->
            val (x, y, width, height, startAngle, arcAngle) = args
            
            x as Int
            y as Int
            width as Int
            height as Int
            startAngle as Int
            arcAngle as Int
            
            window.drawArc(x, y, width, height, startAngle, arcAngle)
        }
        
        add(
            "fillArc",
            DataType.Primitive.NONE,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT
        ) { _, args ->
            val (x, y, width, height, startAngle, arcAngle) = args
            
            x as Int
            y as Int
            width as Int
            height as Int
            startAngle as Int
            arcAngle as Int
            
            window.fillArc(x, y, width, height, startAngle, arcAngle)
        }
        
        add(
            "drawPolyline",
            DataType.Primitive.NONE,
            DataType.Primitive.INT.array,
            DataType.Primitive.INT.array,
            DataType.Primitive.INT
        ) { _, args ->
            val (xPoints, yPoints, nPoints) = args
            
            xPoints as ArrayInstance
            yPoints as ArrayInstance
            nPoints as Int
            
            val xPointsIA = xPoints.asIntArray()
            val yPointsIA = yPoints.asIntArray()
            
            window.drawPolyline(xPointsIA, yPointsIA, nPoints)
        }
        
        add(
            "drawPolygon",
            DataType.Primitive.NONE,
            DataType.Primitive.INT.array,
            DataType.Primitive.INT.array,
            DataType.Primitive.INT
        ) { _, args ->
            val (xPoints, yPoints, nPoints) = args
            
            xPoints as ArrayInstance
            yPoints as ArrayInstance
            nPoints as Int
            
            val xPointsIA = xPoints.asIntArray()
            val yPointsIA = yPoints.asIntArray()
            
            window.drawPolygon(xPointsIA, yPointsIA, nPoints)
        }
        
        add(
            "fillPolygon",
            DataType.Primitive.NONE,
            DataType.Primitive.INT.array,
            DataType.Primitive.INT.array,
            DataType.Primitive.INT
        ) { _, args ->
            val (xPoints, yPoints, nPoints) = args
            
            xPoints as ArrayInstance
            yPoints as ArrayInstance
            nPoints as Int
            
            val xPointsIA = xPoints.asIntArray()
            val yPointsIA = yPoints.asIntArray()
            
            window.fillPolygon(xPointsIA, yPointsIA, nPoints)
        }
        
        add(
            "drawString",
            DataType.Primitive.NONE,
            DataType.Primitive.STRING,
            DataType.Primitive.INT,
            DataType.Primitive.INT
        ) { _, args ->
            val (s, x, y) = args
            
            s as String
            x as Int
            y as Int
            
            window.drawString(s, x, y)
        }
        
        add("loadImage", "Image".data, DataType.Primitive.STRING) { script, args ->
            val (path) = args
            
            path as String
            
            val (id, width, height) = window.loadImage(path)
            
            script.instantiate("Image", id, width, height)
        }
        
        add(
            "drawImage",
            DataType.Primitive.NONE,
            "Image".data,
            DataType.Primitive.INT,
            DataType.Primitive.INT
        ) { _, args ->
            val (image, x, y) = args
            
            image as DataInstance
            x as Int
            y as Int
            
            val (id) = image.deref()
            
            id as Int
            
            window.drawImage(id, x, y)
            
            Unit
        }
        
        add(
            "drawImage",
            DataType.Primitive.NONE,
            "Image".data,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT
        ) { _, args ->
            val (image, x, y, width, height) = args
            
            image as DataInstance
            x as Int
            y as Int
            width as Int
            height as Int
            
            val (id) = image.deref()
            
            id as Int
            
            window.drawImage(id, x, y, width, height)
            
            Unit
        }
        
        add(
            "drawImage",
            DataType.Primitive.NONE,
            "Image".data,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT,
            DataType.Primitive.INT
        ) { _, args ->
            val (image, dxa, dya, dxb, dyb, sxa, sya, sxb, syb) = args
            
            image as DataInstance
            dxa as Int
            dya as Int
            dxb as Int
            dyb as Int
            sxa as Int
            sya as Int
            sxb as Int
            syb as Int
            
            val (id) = image.deref()
            
            id as Int
            
            window.drawImage(id, dxa, dya, dxb, dyb, sxa, sya, sxb, syb)
            
            Unit
        }
        
        add("flip", DataType.Primitive.NONE) { _, _ -> window.flip() }
        
        add("keyIsDown", DataType.Primitive.BOOL, DataType.Primitive.INT) { _, args ->
            val (keyCode) = args
            
            keyCode as Int
            
            window.keyIsDown(keyCode)
        }
        
        add("keyIsHeld", DataType.Primitive.BOOL, DataType.Primitive.INT) { _, args ->
            val (keyCode) = args
            
            keyCode as Int
            
            window.keyIsHeld(keyCode)
        }
        
        add("keyIsUp", DataType.Primitive.BOOL, DataType.Primitive.INT) { _, args ->
            val (keyCode) = args
            
            keyCode as Int
            
            window.keyIsUp(keyCode)
        }
        
        add("buttonIsDown", DataType.Primitive.BOOL, DataType.Primitive.INT) { _, args ->
            val (buttonCode) = args
            
            buttonCode as Int
            
            window.buttonIsDown(buttonCode)
        }
        
        add("buttonIsHeld", DataType.Primitive.BOOL, DataType.Primitive.INT) { _, args ->
            val (buttonCode) = args
            
            buttonCode as Int
            
            window.buttonIsHeld(buttonCode)
        }
        
        add("buttonIsUp", DataType.Primitive.BOOL, DataType.Primitive.INT) { _, args ->
            val (buttonCode) = args
            
            buttonCode as Int
            
            window.buttonIsUp(buttonCode)
        }
        
        add("mouseX", DataType.Primitive.INT) { _, _ -> window.mousePoint.x }
        
        add("mouseY", DataType.Primitive.INT) { _, _ -> window.mousePoint.y }
        
        add("mouseWheel", DataType.Primitive.INT) { _, _ -> window.mouseWheel }
        
        add("poll", DataType.Primitive.NONE) { _, _ -> window.poll() }
    }
    
    private fun add(
        name: String,
        returnType: DataType,
        vararg paramTypes: DataType,
        function: (script: Script, args: List<Any>) -> Any,
    ) {
        val key = paramTypes.joinToString(prefix = "${name.lowercase()}(", separator = ",", postfix = ")")
        
        builtins[key] = Builtin(returnType, function)
    }
    
    operator fun get(sub: Stmt.Sub) =
        builtins[sub.signature]
    
    class Builtin(private val returnType: DataType, val function: (script: Script, args: List<Any>) -> Any) {
        operator fun invoke(script: Script, args: List<Any>): Any? {
            val result = function(script, args)
            
            return returnType.filter(script, result)
        }
    }
}

private operator fun <E> List<E>.component6() = this[5]
private operator fun <E> List<E>.component7() = this[6]
private operator fun <E> List<E>.component8() = this[7]
private operator fun <E> List<E>.component9() = this[8]
