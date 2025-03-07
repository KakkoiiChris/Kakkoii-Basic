@file:Suppress("RedundantUnitExpression")

package kakkoiichris.kb.runtime

import kakkoiichris.kb.parser.Stmt
import kakkoiichris.kb.runtime.DataType.Primitive.*
import kakkoiichris.kb.util.KBError
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
        add("print", NONE, ANY.vararg) { script, args ->
            val (subArgs) = args
            
            subArgs as ArrayInstance
            
            for (arg in subArgs) {
                print(script.getString(arg))
            }
        }
        
        add("input", STRING, ANY.vararg) { script, args ->
            val (subArgs) = args
            
            subArgs as ArrayInstance
            
            for (arg in subArgs) {
                print(script.getString(arg))
            }
            
            readln()
        }
        
        add("read", STRING, STRING) { _, args ->
            val (path) = args
            
            path as String
            
            Files.readString(Paths.get(path))
        }
        
        add("write", NONE, STRING, ANY) { _, args ->
            val (path, data) = args
            
            path as String
            
            Files.writeString(Paths.get(path), data.toString())
            
            Unit
        }
        
        add("concat", STRING, ANY.vararg) { script, args ->
            val (subArgs) = args
            
            subArgs as ArrayInstance
            
            buildString {
                for (arg in subArgs) {
                    append(script.getString(arg))
                }
            }
        }
        
        add("sleep", NONE, LONG) { _, args ->
            val (milliseconds) = args
            
            milliseconds as Long
            
            Thread.sleep(milliseconds)
        }
        
        add("milliseconds", LONG) { _, _ -> System.currentTimeMillis() }
        
        add("nanoseconds", LONG) { _, _ -> System.nanoTime() }
        
        add("typeof", STRING, ANY) { script, args ->
            val (x) = args
            
            DataType.infer(script, x).toString()
        }
        
        add("invoke", ANY, STRING, ANY.vararg) { script, args ->
            val (name, arguments) = args
            
            name as String
            arguments as ArrayInstance
            
            script.invoke(name, *arguments.toTypedArray()) ?: KBError.noSub(name)
        }
        
        add("exit", NONE, INT) { _, args ->
            val (code) = args
            
            code as Int
            
            exitProcess(code)
        }
    }
    
    private fun addChar() {
        add("isAlpha", BOOL, CHAR) { _, args ->
            val (c) = args
            
            c as Char
            
            c.isLetter()
        }
        
        add("isAlnum", BOOL, CHAR) { _, args ->
            val (c) = args
            
            c as Char
            
            c.isLetterOrDigit()
        }
        
        add("isDigit", BOOL, CHAR) { _, args ->
            val (c) = args
            
            c as Char
            
            c.isDigit()
        }
        
        add("isSpace", BOOL, CHAR) { _, args ->
            val (c) = args
            
            c as Char
            
            c.isWhitespace()
        }
        
        add("isVowel", BOOL, CHAR) { _, args ->
            val (c) = args
            
            c as Char
            
            c in "AEIOUaeiou"
        }
        
        add("isLower", BOOL, CHAR) { _, args ->
            val (c) = args
            
            c as Char
            
            c.isLowerCase()
        }
        
        add("isUpper", BOOL, CHAR) { _, args ->
            val (c) = args
            
            c as Char
            
            c.isUpperCase()
        }
        
        add("toLower", CHAR, CHAR) { _, args ->
            val (c) = args
            
            c as Char
            
            c.lowercase()
        }
        
        add("toUpper", CHAR, CHAR) { _, args ->
            val (c) = args
            
            c as Char
            
            c.uppercase()
        }
    }
    
    private fun addString() {
        add("isBlank", BOOL, STRING) { _, args ->
            val (s) = args
            
            s as String
            
            s.isBlank()
        }
        
        add("isEmpty", BOOL, STRING) { _, args ->
            val (s) = args
            
            s as String
            
            s.isBlank()
        }
        
        add("toLower", STRING, STRING) { _, args ->
            val (s) = args
            
            s as String
            
            s.lowercase()
        }
        
        add("toUpper", STRING, STRING) { _, args ->
            val (s) = args
            
            s as String
            
            s.uppercase()
        }
        
        add(
            "startsWith",
            BOOL,
            STRING,
            STRING,
            BOOL
        ) { _, args ->
            val (s, prefix, ignoreCase) = args
            
            s as String
            prefix as String
            ignoreCase as Boolean
            
            s.startsWith(prefix, ignoreCase)
        }
        
        add(
            "endsWith",
            BOOL,
            STRING,
            STRING,
            BOOL
        ) { _, args ->
            val (s, suffix, ignoreCase) = args
            
            s as String
            suffix as String
            ignoreCase as Boolean
            
            s.endsWith(suffix, ignoreCase)
        }
        
        add("trim", STRING, STRING) { _, args ->
            val (s) = args
            
            s as String
            
            s.trim()
        }
        
        add("trimStart", STRING, STRING) { _, args ->
            val (s) = args
            
            s as String
            
            s.trimStart()
        }
        
        add("trimEnd", STRING, STRING) { _, args ->
            val (s) = args
            
            s as String
            
            s.trimEnd()
        }
        
        add(
            "substring",
            STRING,
            STRING,
            INT,
            INT
        ) { _, args ->
            val (s, startIndex, endIndex) = args
            
            s as String
            startIndex as Int
            endIndex as Int
            
            s.substring(startIndex, endIndex)
        }
        
        add(
            "split",
            STRING.array,
            STRING,
            STRING
        ) { _, args ->
            
            val (s, regex) = args
            
            s as String
            regex as String
            
            s.split(regex.toRegex()).toTypedArray().toArrayInstance()
        }
        
        add("indexOf", INT, STRING, CHAR) { _, args ->
            val (s, c) = args
            
            s as String
            c as Char
            
            s.indexOf(c)
        }
        
        add("indexOf", INT, STRING, STRING) { _, args ->
            val (s, sequence) = args
            
            s as String
            sequence as String
            
            s.indexOf(sequence)
        }
        
        add("lastIndexOf", INT, STRING, CHAR) { _, args ->
            val (s, c) = args
            
            s as String
            c as Char
            
            s.lastIndexOf(c)
        }
        
        add("lastIndexOf", INT, STRING, STRING) { _, args ->
            val (s, sequence) = args
            
            s as String
            sequence as String
            
            s.lastIndexOf(sequence)
        }
        
        add(
            "padStart",
            STRING,
            STRING,
            INT,
            CHAR
        ) { _, args ->
            val (s, length, c) = args
            
            s as String
            length as Int
            c as Char
            
            s.padStart(length, c)
        }
        
        add(
            "padEnd",
            STRING,
            STRING,
            INT,
            CHAR
        ) { _, args ->
            val (s, length, c) = args
            
            s as String
            length as Int
            c as Char
            
            s.padEnd(length, c)
        }
        
        add(
            "replace",
            STRING,
            STRING,
            CHAR,
            CHAR
        ) { _, args ->
            val (s, old, new) = args
            
            s as String
            old as Char
            new as Char
            
            s.replace(old, new)
        }
        
        add(
            "replace",
            STRING,
            STRING,
            STRING,
            STRING
        ) { _, args ->
            val (s, old, new) = args
            
            s as String
            old as String
            new as String
            
            s.replace(old, new)
        }
    }
    
    private fun addMath() {
        add("abs", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            abs(n)
        }
        
        add("abs", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            abs(n)
        }
        
        add("abs", INT, INT) { _, args ->
            val (n) = args
            
            n as Int
            
            abs(n)
        }
        
        add("abs", LONG, LONG) { _, args ->
            val (n) = args
            
            n as Long
            
            abs(n)
        }
        
        add("acos", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            acos(n)
        }
        
        add("acos", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            acos(n)
        }
        
        add("acosh", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            acosh(n)
        }
        
        add("acosh", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            acosh(n)
        }
        
        add("asin", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            asin(n)
        }
        
        add("asin", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            asin(n)
        }
        
        add("asinh", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            asinh(n)
        }
        
        add("asinh", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            asinh(n)
        }
        
        add("atan", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            atan(n)
        }
        
        add("atan", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            atan(n)
        }
        
        add(
            "atan2",
            DOUBLE, DOUBLE, DOUBLE
        ) { _, args ->
            val (y, x) = args
            
            y as Double
            x as Double
            
            atan2(y, x)
        }
        
        add("atan2", FLOAT, FLOAT, FLOAT) { _, args ->
            val (y, x) = args
            
            y as Float
            x as Float
            
            atan2(y, x)
        }
        
        add("atanh", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            atanh(n)
        }
        
        add("atanh", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            atanh(n)
        }
        
        add("ceil", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            ceil(n)
        }
        
        add("ceil", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            ceil(n)
        }
        
        add("cos", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            cos(n)
        }
        
        add("cos", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            cos(n)
        }
        
        add("cosh", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            cosh(n)
        }
        
        add("cosh", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            cosh(n)
        }
        
        add("exp", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            exp(n)
        }
        
        add("exp", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            exp(n)
        }
        
        add("expm1", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            expm1(n)
        }
        
        add("expm1", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            expm1(n)
        }
        
        add("floor", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            floor(n)
        }
        
        add("floor", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            floor(n)
        }
        
        add(
            "hypot",
            DOUBLE, DOUBLE, DOUBLE
        ) { _, args ->
            val (x, y) = args
            
            x as Double
            y as Double
            
            hypot(x, y)
        }
        
        add("hypot", FLOAT, FLOAT, FLOAT) { _, args ->
            val (x, y) = args
            
            x as Float
            y as Float
            
            hypot(x, y)
        }
        
        add("IEEErem", DOUBLE, DOUBLE, DOUBLE) { _, args ->
            val (n, divisor) = args
            
            n as Double
            divisor as Double
            
            n.IEEErem(divisor)
        }
        
        add("IEEErem", FLOAT, FLOAT, FLOAT) { _, args ->
            val (n, divisor) = args
            
            n as Float
            divisor as Float
            
            n.IEEErem(divisor)
        }
        
        add("ln", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            ln(n)
        }
        
        add("ln", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            ln(n)
        }
        
        add("ln1p", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            ln1p(n)
        }
        
        add("ln1p", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            ln1p(n)
        }
        
        add(
            "log",
            DOUBLE, DOUBLE, DOUBLE
        ) { _, args ->
            val (n, base) = args
            
            n as Double
            base as Double
            
            log(n, base)
        }
        
        add("log", FLOAT, FLOAT, FLOAT) { _, args ->
            val (n, base) = args
            
            n as Float
            base as Float
            
            log(n, base)
        }
        
        add("log10", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            log10(n)
        }
        
        add("log10", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            log10(n)
        }
        
        add("log2", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            log2(n)
        }
        
        add("log2", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            log2(n)
        }
        
        add("max", DOUBLE, DOUBLE, DOUBLE) { _, args ->
            val (a, b) = args
            
            a as Double
            b as Double
            
            max(a, b)
        }
        
        add("max", FLOAT, FLOAT, FLOAT) { _, args ->
            val (a, b) = args
            
            a as Float
            b as Float
            
            max(a, b)
        }
        
        add("max", INT, INT, INT) { _, args ->
            val (a, b) = args
            
            a as Int
            b as Int
            
            max(a, b)
        }
        
        add("max", LONG, LONG, LONG) { _, args ->
            val (a, b) = args
            
            a as Long
            b as Long
            
            max(a, b)
        }
        
        add("min", DOUBLE, DOUBLE, DOUBLE) { _, args ->
            val (a, b) = args
            
            a as Double
            b as Double
            
            min(a, b)
        }
        
        add("min", FLOAT, FLOAT, FLOAT) { _, args ->
            val (a, b) = args
            
            a as Float
            b as Float
            
            min(a, b)
        }
        
        add("min", INT, INT, INT) { _, args ->
            val (a, b) = args
            
            a as Int
            b as Int
            
            min(a, b)
        }
        
        add("min", LONG, LONG, LONG) { _, args ->
            val (a, b) = args
            
            a as Long
            b as Long
            
            min(a, b)
        }
        
        add("nextdown", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            n.nextDown()
        }
        
        add("nextdown", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            n.nextDown()
        }
        
        add(
            "nexttowards",
            DOUBLE, DOUBLE, DOUBLE
        ) { _, args ->
            val (a, b) = args
            
            a as Double
            b as Double
            
            a.nextTowards(b)
        }
        
        add(
            "nexttowards",
            FLOAT, FLOAT, FLOAT
        ) { _, args ->
            val (a, b) = args
            
            a as Float
            b as Float
            
            a.nextTowards(b)
        }
        
        add("nextup", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            n.nextUp()
        }
        
        add("nextup", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            n.nextUp()
        }
        
        add("pow", DOUBLE, DOUBLE, DOUBLE) { _, args ->
            val (b, e) = args
            
            b as Double
            e as Double
            
            b.pow(e)
        }
        
        add("pow", FLOAT, FLOAT, FLOAT) { _, args ->
            val (b, e) = args
            
            b as Float
            e as Float
            
            b.pow(e)
        }
        
        add("pow", DOUBLE, DOUBLE, INT) { _, args ->
            val (b, e) = args
            
            b as Double
            e as Int
            
            b.pow(e)
        }
        
        add("pow", FLOAT, FLOAT, INT) { _, args ->
            val (b, e) = args
            
            b as Float
            e as Int
            
            b.pow(e)
        }
        
        add("random", DOUBLE) { _, _ -> Math.random() }
        
        add("round", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            round(n)
        }
        
        add("round", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            round(n)
        }
        
        add("roundToInt", INT, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            n.roundToInt()
        }
        
        add("roundToInt", INT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            n.roundToInt()
        }
        
        add("roundToLong", LONG, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            n.roundToLong()
        }
        
        add("roundToLong", LONG, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            n.roundToLong()
        }
        
        add("sign", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            n.sign
        }
        
        add("sign", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            n.sign
        }
        
        add("sign", INT, INT) { _, args ->
            val (n) = args
            
            n as Int
            
            n.sign
        }
        
        add("sign", INT, LONG) { _, args ->
            val (n) = args
            
            n as Long
            
            n.sign
        }
        
        add("sin", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            sin(n)
        }
        
        add("sin", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            sin(n)
        }
        
        add("sinh", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            sinh(n)
        }
        
        add("sinh", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            sinh(n)
        }
        
        add("sqrt", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            sqrt(n)
        }
        
        add("sqrt", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            sqrt(n)
        }
        
        add("tan", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            tan(n)
        }
        
        add("tan", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            tan(n)
        }
        
        add("tanh", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            tanh(n)
        }
        
        add("tanh", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            tanh(n)
        }
        
        add("truncate", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            truncate(n)
        }
        
        add("truncate", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            truncate(n)
        }
        
        add("ulp", DOUBLE, DOUBLE) { _, args ->
            val (n) = args
            
            n as Double
            
            n.ulp
        }
        
        add("ulp", FLOAT, FLOAT) { _, args ->
            val (n) = args
            
            n as Float
            
            n.ulp
        }
        
        add(
            "map",
            DOUBLE,
            DOUBLE,
            DOUBLE,
            DOUBLE,
            DOUBLE,
            DOUBLE
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
            FLOAT,
            FLOAT,
            FLOAT,
            FLOAT,
            FLOAT,
            FLOAT
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
            INT,
            INT,
            INT,
            INT,
            INT,
            INT
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
            LONG,
            LONG,
            LONG,
            LONG,
            LONG,
            LONG
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
            NONE,
            INT,
            INT,
            STRING
        ) { _, args ->
            val (width, height, title) = args
            
            width as Int
            height as Int
            title as String
            
            window = Window(width, height, title)
            
            Unit
        }
        
        add("openWindow", NONE) { _, _ -> window.open() }
        
        add("closeWindow", NONE) { _, _ -> window.close() }
        
        add("windowIsOpen", BOOL) { _, _ -> window.isOpen }
        
        add("getColor", "Color".data) { script, _ ->
            val color = window.getColor()
            
            script.instantiate("Color", color.red, color.green, color.blue, color.alpha)
        }
        
        add(
            "setColor",
            NONE,
            INT,
            INT,
            INT,
            INT
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
            DOUBLE,
            DOUBLE,
            DOUBLE
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
            NONE,
            STRING,
            INT,
            INT
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
            NONE,
            FLOAT,
            INT,
            INT,
            FLOAT,
            FLOAT.array,
            FLOAT
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
        
        add("translate", NONE, INT, INT) { _, args ->
            val (x, y) = args
            
            x as Int
            y as Int
            
            window.translate(x, y)
        }
        
        add("translate", NONE, DOUBLE, DOUBLE) { _, args ->
            val (x, y) = args
            
            x as Double
            y as Double
            
            window.translate(x, y)
        }
        
        add("rotate", NONE, DOUBLE) { _, args ->
            val (theta) = args
            
            theta as Double
            
            window.rotate(theta)
        }
        
        add(
            "rotate",
            NONE,
            DOUBLE,
            DOUBLE,
            DOUBLE
        ) { _, args ->
            val (theta, x, y) = args
            
            theta as Double
            x as Double
            y as Double
            
            window.rotate(theta, x, y)
        }
        
        add("scale", NONE, DOUBLE, DOUBLE) { _, args ->
            val (x, y) = args
            
            x as Double
            y as Double
            
            window.scale(x, y)
        }
        
        add("shear", NONE, DOUBLE, DOUBLE) { _, args ->
            val (x, y) = args
            
            x as Double
            y as Double
            
            window.shear(x, y)
        }
        
        add("pushMatrix", NONE) { _, _ -> window.pushMatrix() }
        
        add("popMatrix", NONE) { _, _ -> window.popMatrix() }
        
        add("clear", NONE) { _, _ -> window.clear() }
        
        add(
            "drawLine",
            NONE,
            INT,
            INT,
            INT,
            INT
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
            NONE,
            INT,
            INT,
            INT,
            INT
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
            NONE,
            INT,
            INT,
            INT,
            INT
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
            NONE,
            INT,
            INT,
            INT,
            INT
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
            NONE,
            INT,
            INT,
            INT,
            INT
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
            NONE,
            INT,
            INT,
            INT,
            INT,
            INT,
            INT
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
            NONE,
            INT,
            INT,
            INT,
            INT,
            INT,
            INT
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
            NONE,
            INT,
            INT,
            INT,
            INT,
            BOOL
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
            NONE,
            INT,
            INT,
            INT,
            INT,
            BOOL
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
            NONE,
            INT,
            INT,
            INT,
            INT,
            INT,
            INT
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
            NONE,
            INT,
            INT,
            INT,
            INT,
            INT,
            INT
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
            NONE,
            INT.array,
            INT.array,
            INT
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
            NONE,
            INT.array,
            INT.array,
            INT
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
            NONE,
            INT.array,
            INT.array,
            INT
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
            NONE,
            STRING,
            INT,
            INT
        ) { _, args ->
            val (s, x, y) = args
            
            s as String
            x as Int
            y as Int
            
            window.drawString(s, x, y)
        }
        
        add("loadImage", "Image".data, STRING) { script, args ->
            val (path) = args
            
            path as String
            
            val (id, width, height) = window.loadImage(path)
            
            script.instantiate("Image", id, width, height)
        }
        
        add(
            "drawImage",
            NONE,
            "Image".data,
            INT,
            INT
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
            NONE,
            "Image".data,
            INT,
            INT,
            INT,
            INT
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
            NONE,
            "Image".data,
            INT,
            INT,
            INT,
            INT,
            INT,
            INT,
            INT,
            INT
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
        
        add("flip", NONE) { _, _ -> window.flip() }
        
        add("keyIsDown", BOOL, INT) { _, args ->
            val (keyCode) = args
            
            keyCode as Int
            
            window.keyIsDown(keyCode)
        }
        
        add("keyIsHeld", BOOL, INT) { _, args ->
            val (keyCode) = args
            
            keyCode as Int
            
            window.keyIsHeld(keyCode)
        }
        
        add("keyIsUp", BOOL, INT) { _, args ->
            val (keyCode) = args
            
            keyCode as Int
            
            window.keyIsUp(keyCode)
        }
        
        add("buttonIsDown", BOOL, INT) { _, args ->
            val (buttonCode) = args
            
            buttonCode as Int
            
            window.buttonIsDown(buttonCode)
        }
        
        add("buttonIsHeld", BOOL, INT) { _, args ->
            val (buttonCode) = args
            
            buttonCode as Int
            
            window.buttonIsHeld(buttonCode)
        }
        
        add("buttonIsUp", BOOL, INT) { _, args ->
            val (buttonCode) = args
            
            buttonCode as Int
            
            window.buttonIsUp(buttonCode)
        }
        
        add("mouseX", INT) { _, _ -> window.mousePoint.x }
        
        add("mouseY", INT) { _, _ -> window.mousePoint.y }
        
        add("mouseWheel", INT) { _, _ -> window.mouseWheel }
        
        add("poll", NONE) { _, _ -> window.poll() }
    }
    
    private fun add(
        name: String,
        returnType: DataType,
        vararg paramTypes: DataType,
        function: (runtime: Runtime, args: List<Any>) -> Any,
    ) {
        val key = paramTypes.joinToString(prefix = "${name.lowercase()}(", separator = ",", postfix = ")")
        
        builtins[key] = Builtin(returnType, function)
    }
    
    operator fun get(sub: Stmt.Sub) =
        builtins[sub.signature]
    
    class Builtin(private val returnType: DataType, val function: (runtime: Runtime, args: List<Any>) -> Any) {
        operator fun invoke(runtime: Runtime, args: List<Any>): Any? {
            val result = function(runtime, args)
            
            return returnType.filter(runtime, result)
        }
    }
}

private operator fun <E> List<E>.component6() = this[5]
private operator fun <E> List<E>.component7() = this[6]
private operator fun <E> List<E>.component8() = this[7]
private operator fun <E> List<E>.component9() = this[8]
