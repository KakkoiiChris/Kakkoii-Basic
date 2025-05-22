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
        add("print", NONE, ANY.vararg) { runtime, args ->
            val (subArgs) = args

            subArgs as KBArray

            for (arg in subArgs.value) {
                print(runtime.getString(arg.value!!))
            }

            KBNone
        }

        add("input", STRING, ANY.vararg) { runtime, args ->
            val (subArgs) = args

            subArgs as KBArray

            for (arg in subArgs.value) {
                print(runtime.getString(arg))
            }

            KBString(readln())
        }

        add("read", STRING, STRING) { _, args ->
            val (path) = args

            path as KBString

            KBString(Files.readString(Paths.get(path.value)))
        }

        add("write", NONE, STRING, ANY) { _, args ->
            val (path, data) = args

            path as KBString

            Files.writeString(Paths.get(path.value), data.value.toString())

            KBNone
        }

        add("concat", STRING, ANY.vararg) { runtime, args ->
            val (subArgs) = args

            subArgs as KBArray

            KBString(buildString {
                for (arg in subArgs.value) {
                    append(runtime.getString(arg))
                }
            })
        }

        add("sleep", NONE, LONG) { _, args ->
            val (milliseconds) = args

            milliseconds as KBLong

            Thread.sleep(milliseconds.value)

            KBNone
        }

        add("milliseconds", LONG) { _, _ -> KBLong(System.currentTimeMillis()) }

        add("nanoseconds", LONG) { _, _ -> KBLong(System.nanoTime()) }

        add("typeof", STRING, ANY) { runtime, args ->
            val (x) = args

            KBString(DataType.infer(runtime, x).toString())
        }

        add("invoke", ANY, STRING, ANY.vararg) { runtime, args ->
            val (name, arguments) = args

            name as KBString
            arguments as KBArray

            runtime.invoke(name.value, *arguments.value.toTypedArray())?.result ?: KBError.noSub(name.value)
        }

        add("exit", NONE, INT) { _, args ->
            val (code) = args

            code as KBInt

            exitProcess(code.value)
        }
    }

    private fun addChar() {
        add("isAlpha", BOOL, CHAR) { _, args ->
            val (c) = args

            c as KBChar

            KBBool(c.value.isLetter())
        }

        add("isAlnum", BOOL, CHAR) { _, args ->
            val (c) = args

            c as KBChar

            KBBool(c.value.isLetterOrDigit())
        }

        add("isDigit", BOOL, CHAR) { _, args ->
            val (c) = args

            c as KBChar

            KBBool(c.value.isDigit())
        }

        add("isSpace", BOOL, CHAR) { _, args ->
            val (c) = args

            c as KBChar

            KBBool(c.value.isWhitespace())
        }

        add("isVowel", BOOL, CHAR) { _, args ->
            val (c) = args

            c as KBChar

            KBBool(c.value in "AEIOUaeiou")
        }

        add("isLower", BOOL, CHAR) { _, args ->
            val (c) = args

            c as KBChar

            KBBool(c.value.isLowerCase())
        }

        add("isUpper", BOOL, CHAR) { _, args ->
            val (c) = args

            c as KBChar

            KBBool(c.value.isUpperCase())
        }

        add("toLower", CHAR, CHAR) { _, args ->
            val (c) = args

            c as KBChar

            KBChar(c.value.lowercase().first())
        }

        add("toUpper", CHAR, CHAR) { _, args ->
            val (c) = args

            c as KBChar

            KBChar(c.value.uppercase().first())
        }
    }

    private fun addString() {
        add("isBlank", BOOL, STRING) { _, args ->
            val (s) = args

            s as KBString

            KBBool(s.value.isBlank())
        }

        add("isEmpty", BOOL, STRING) { _, args ->
            val (s) = args

            s as KBString

            KBBool(s.value.isBlank())
        }

        add("toLower", STRING, STRING) { _, args ->
            val (s) = args

            s as KBString

            KBString(s.value.lowercase())
        }

        add("toUpper", STRING, STRING) { _, args ->
            val (s) = args

            s as KBString

            KBString(s.value.uppercase())
        }

        add(
            "startsWith",
            BOOL,
            STRING,
            STRING,
            BOOL
        ) { _, args ->
            val (s, prefix, ignoreCase) = args

            s as KBString
            prefix as KBString
            ignoreCase as KBBool

            KBBool(s.value.startsWith(prefix.value, ignoreCase.value))
        }

        add(
            "endsWith",
            BOOL,
            STRING,
            STRING,
            BOOL
        ) { _, args ->
            val (s, suffix, ignoreCase) = args

            s as KBString
            suffix as KBString
            ignoreCase as KBBool

            KBBool(s.value.endsWith(suffix.value, ignoreCase.value))
        }

        add("trim", STRING, STRING) { _, args ->
            val (s) = args

            s as KBString

            KBString(s.value.trim())
        }

        add("trimStart", STRING, STRING) { _, args ->
            val (s) = args

            s as KBString

            KBString(s.value.trimStart())
        }

        add("trimEnd", STRING, STRING) { _, args ->
            val (s) = args

            s as KBString

            KBString(s.value.trimEnd())
        }

        add(
            "substring",
            STRING,
            STRING,
            INT,
            INT
        ) { _, args ->
            val (s, startIndex, endIndex) = args

            s as KBString
            startIndex as KBInt
            endIndex as KBInt

            KBString(s.value.substring(startIndex.value, endIndex.value))
        }

        add(
            "split",
            STRING.array,
            STRING,
            STRING
        ) { _, args ->

            val (s, regex) = args

            s as KBString
            regex as KBString

            KBArray(s.value.split(regex.value.toRegex()).toTypedArray().toArrayInstance())
        }

        add("indexOf", INT, STRING, CHAR) { _, args ->
            val (s, c) = args

            s as KBString
            c as KBChar

            KBInt(s.value.indexOf(c.value))
        }

        add("indexOf", INT, STRING, STRING) { _, args ->
            val (s, sequence) = args

            s as KBString
            sequence as KBString

            KBInt(s.value.indexOf(sequence.value))
        }

        add("lastIndexOf", INT, STRING, CHAR) { _, args ->
            val (s, c) = args

            s as KBString
            c as KBChar

            KBInt(s.value.lastIndexOf(c.value))
        }

        add("lastIndexOf", INT, STRING, STRING) { _, args ->
            val (s, sequence) = args

            s as KBString
            sequence as KBString

            KBInt(s.value.lastIndexOf(sequence.value))
        }

        add(
            "padStart",
            STRING,
            STRING,
            INT,
            CHAR
        ) { _, args ->
            val (s, length, c) = args

            s as KBString
            length as KBInt
            c as KBChar

            KBString(s.value.padStart(length.value, c.value))
        }

        add(
            "padEnd",
            STRING,
            STRING,
            INT,
            CHAR
        ) { _, args ->
            val (s, length, c) = args

            s as KBString
            length as KBInt
            c as KBChar

            KBString(s.value.padEnd(length.value, c.value))
        }

        add(
            "replace",
            STRING,
            STRING,
            CHAR,
            CHAR
        ) { _, args ->
            val (s, old, new) = args

            s as KBString
            old as KBChar
            new as KBChar

            KBString(s.value.replace(old.value, new.value))
        }

        add(
            "replace",
            STRING,
            STRING,
            STRING,
            STRING
        ) { _, args ->
            val (s, old, new) = args

            s as KBString
            old as KBString
            new as KBString

            KBString(s.value.replace(old.value, new.value))
        }
    }

    private fun addMath() {
        add("abs", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(abs(n.value))
        }

        add("abs", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(abs(n.value))
        }

        add("abs", INT, INT) { _, args ->
            val (n) = args

            n as KBInt

            KBInt(abs(n.value))
        }

        add("abs", LONG, LONG) { _, args ->
            val (n) = args

            n as KBLong

            KBLong(abs(n.value))
        }

        add("acos", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(acos(n.value))
        }

        add("acos", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(acos(n.value))
        }

        add("acosh", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(acosh(n.value))
        }

        add("acosh", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(acosh(n.value))
        }

        add("asin", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(asin(n.value))
        }

        add("asin", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(asin(n.value))
        }

        add("asinh", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(asinh(n.value))
        }

        add("asinh", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(asinh(n.value))
        }

        add("atan", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(atan(n.value))
        }

        add("atan", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(atan(n.value))
        }

        add(
            "atan2",
            DOUBLE, DOUBLE, DOUBLE
        ) { _, args ->
            val (y, x) = args

            y as KBDouble
            x as KBDouble

            KBDouble(atan2(y.value, x.value))
        }

        add("atan2", FLOAT, FLOAT, FLOAT) { _, args ->
            val (y, x) = args

            y as KBFloat
            x as KBFloat

            KBFloat(atan2(y.value, x.value))
        }

        add("atanh", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(atanh(n.value))
        }

        add("atanh", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(atanh(n.value))
        }

        add("ceil", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(ceil(n.value))
        }

        add("ceil", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(ceil(n.value))
        }

        add("cos", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(cos(n.value))
        }

        add("cos", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(cos(n.value))
        }

        add("cosh", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(cosh(n.value))
        }

        add("cosh", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(cosh(n.value))
        }

        add("exp", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(exp(n.value))
        }

        add("exp", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(exp(n.value))
        }

        add("expm1", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(expm1(n.value))
        }

        add("expm1", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(expm1(n.value))
        }

        add("floor", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(floor(n.value))
        }

        add("floor", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(floor(n.value))
        }

        add(
            "hypot",
            DOUBLE, DOUBLE, DOUBLE
        ) { _, args ->
            val (x, y) = args

            x as KBDouble
            y as KBDouble

            KBDouble(hypot(x.value, y.value))
        }

        add("hypot", FLOAT, FLOAT, FLOAT) { _, args ->
            val (x, y) = args

            x as KBFloat
            y as KBFloat

            KBFloat(hypot(x.value, y.value))
        }

        add("IEEErem", DOUBLE, DOUBLE, DOUBLE) { _, args ->
            val (n, divisor) = args

            n as KBDouble
            divisor as KBDouble

            KBDouble(n.value.IEEErem(divisor.value))
        }

        add("IEEErem", FLOAT, FLOAT, FLOAT) { _, args ->
            val (n, divisor) = args

            n as KBFloat
            divisor as KBFloat

            KBFloat(n.value.IEEErem(divisor.value))
        }

        add("ln", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(ln(n.value))
        }

        add("ln", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(ln(n.value))
        }

        add("ln1p", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(ln1p(n.value))
        }

        add("ln1p", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(ln1p(n.value))
        }

        add(
            "log",
            DOUBLE, DOUBLE, DOUBLE
        ) { _, args ->
            val (n, base) = args

            n as KBDouble
            base as KBDouble

            KBDouble(log(n.value, base.value))
        }

        add("log", FLOAT, FLOAT, FLOAT) { _, args ->
            val (n, base) = args

            n as KBFloat
            base as KBFloat

            KBFloat(log(n.value, base.value))
        }

        add("log10", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(log10(n.value))
        }

        add("log10", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(log10(n.value))
        }

        add("log2", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(log2(n.value))
        }

        add("log2", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(log2(n.value))
        }

        add("max", DOUBLE, DOUBLE, DOUBLE) { _, args ->
            val (a, b) = args

            a as KBDouble
            b as KBDouble

            KBDouble(max(a.value, b.value))
        }

        add("max", FLOAT, FLOAT, FLOAT) { _, args ->
            val (a, b) = args

            a as KBFloat
            b as KBFloat

            KBFloat(max(a.value, b.value))
        }

        add("max", INT, INT, INT) { _, args ->
            val (a, b) = args

            a as KBInt
            b as KBInt

            KBInt(max(a.value, b.value))
        }

        add("max", LONG, LONG, LONG) { _, args ->
            val (a, b) = args

            a as KBLong
            b as KBLong

            KBLong(max(a.value, b.value))
        }

        add("min", DOUBLE, DOUBLE, DOUBLE) { _, args ->
            val (a, b) = args

            a as KBDouble
            b as KBDouble

            KBDouble(min(a.value, b.value))
        }

        add("min", FLOAT, FLOAT, FLOAT) { _, args ->
            val (a, b) = args

            a as KBFloat
            b as KBFloat

            KBFloat(min(a.value, b.value))
        }

        add("min", INT, INT, INT) { _, args ->
            val (a, b) = args

            a as KBInt
            b as KBInt

            KBInt(min(a.value, b.value))
        }

        add("min", LONG, LONG, LONG) { _, args ->
            val (a, b) = args

            a as KBLong
            b as KBLong

            KBLong(min(a.value, b.value))
        }

        add("nextdown", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(n.value.nextDown())
        }

        add("nextdown", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(n.value.nextDown())
        }

        add(
            "nexttowards",
            DOUBLE, DOUBLE, DOUBLE
        ) { _, args ->
            val (a, b) = args

            a as KBDouble
            b as KBDouble

            KBDouble(a.value.nextTowards(b.value))
        }

        add(
            "nexttowards",
            FLOAT, FLOAT, FLOAT
        ) { _, args ->
            val (a, b) = args

            a as KBFloat
            b as KBFloat

            KBFloat(a.value.nextTowards(b.value))
        }

        add("nextup", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(n.value.nextUp())
        }

        add("nextup", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(n.value.nextUp())
        }

        add("pow", DOUBLE, DOUBLE, DOUBLE) { _, args ->
            val (b, e) = args

            b as KBDouble
            e as KBDouble

            KBDouble(b.value.pow(e.value))
        }

        add("pow", FLOAT, FLOAT, FLOAT) { _, args ->
            val (b, e) = args

            b as KBFloat
            e as KBFloat

            KBFloat(b.value.pow(e.value))
        }

        add("pow", DOUBLE, DOUBLE, INT) { _, args ->
            val (b, e) = args

            b as KBDouble
            e as KBInt

            KBDouble(b.value.pow(e.value))
        }

        add("pow", FLOAT, FLOAT, INT) { _, args ->
            val (b, e) = args

            b as KBFloat
            e as KBInt

            KBFloat(b.value.pow(e.value))
        }

        add("random", DOUBLE) { _, _ -> KBDouble(Math.random()) }

        add("round", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(round(n.value))
        }

        add("round", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(round(n.value))
        }

        add("roundToInt", INT, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBInt(n.value.roundToInt())
        }

        add("roundToInt", INT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBInt(n.value.roundToInt())
        }

        add("roundToLong", LONG, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBLong(n.value.roundToLong())
        }

        add("roundToLong", LONG, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBLong(n.value.roundToLong())
        }

        add("sign", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(n.value.sign)
        }

        add("sign", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(n.value.sign)
        }

        add("sign", INT, INT) { _, args ->
            val (n) = args

            n as KBInt

            KBInt(n.value.sign)
        }

        add("sign", INT, LONG) { _, args ->
            val (n) = args

            n as KBLong

            KBInt(n.value.sign)
        }

        add("sin", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(sin(n.value))
        }

        add("sin", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(sin(n.value))
        }

        add("sinh", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(sinh(n.value))
        }

        add("sinh", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(sinh(n.value))
        }

        add("sqrt", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(sqrt(n.value))
        }

        add("sqrt", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(sqrt(n.value))
        }

        add("tan", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(tan(n.value))
        }

        add("tan", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(tan(n.value))
        }

        add("tanh", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(tanh(n.value))
        }

        add("tanh", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(tanh(n.value))
        }

        add("truncate", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(truncate(n.value))
        }

        add("truncate", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(truncate(n.value))
        }

        add("ulp", DOUBLE, DOUBLE) { _, args ->
            val (n) = args

            n as KBDouble

            KBDouble(n.value.ulp)
        }

        add("ulp", FLOAT, FLOAT) { _, args ->
            val (n) = args

            n as KBFloat

            KBFloat(n.value.ulp)
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

            n as KBDouble
            fromMin as KBDouble
            fromMax as KBDouble
            toMin as KBDouble
            toMax as KBDouble

            KBDouble((n.value - fromMin.value) * (toMax.value - toMin.value) / (fromMax.value - fromMin.value) + toMin.value)
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

            n as KBFloat
            fromMin as KBFloat
            fromMax as KBFloat
            toMin as KBFloat
            toMax as KBFloat

            KBFloat((n.value - fromMin.value) * (toMax.value - toMin.value) / (fromMax.value - fromMin.value) + toMin.value)
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

            n as KBInt
            fromMin as KBInt
            fromMax as KBInt
            toMin as KBInt
            toMax as KBInt

            KBInt(((n.value - fromMin.value) * (toMax.value - toMin.value) / (fromMax.value - fromMin.value).toDouble() + toMin.value).toInt())
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

            n as KBLong
            fromMin as KBLong
            fromMax as KBLong
            toMin as KBLong
            toMax as KBLong

            KBLong(((n.value - fromMin.value) * (toMax.value - toMin.value) / (fromMax.value - fromMin.value).toDouble() + toMin.value).toLong())
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

            width as KBInt
            height as KBInt
            title as KBString

            window = Window(width.value, height.value, title.value)

            KBNone
        }

        add("openWindow", NONE) { _, _ ->
            window.open()
            KBNone
        }

        add("closeWindow", NONE) { _, _ ->
            window.close()
            KBNone
        }

        add("windowIsOpen", BOOL) { _, _ -> KBBool(window.isOpen) }

        add("getColor", "Color".data) { runtime, _ ->
            val color = window.getColor()

            runtime.instantiate("Color", color.red, color.green, color.blue, color.alpha)
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

            red as KBInt
            green as KBInt
            blue as KBInt
            alpha as KBInt

            window.setColor(red.value, green.value, blue.value, alpha.value)

            KBNone
        }

        add(
            "hsbToColor",
            "Color".data,
            DOUBLE,
            DOUBLE,
            DOUBLE
        ) { runtime, args ->
            val (h, s, b) = args

            h as KBDouble
            s as KBDouble
            b as KBDouble

            val color = Color(Color.HSBtoRGB(h.value.toFloat(), s.value.toFloat(), b.value.toFloat()))

            runtime.instantiate("Color", color.red, color.green, color.blue, color.alpha)
        }

        add("getFont", "Font".data) { runtime, _ ->
            val font = window.getFont()

            runtime.instantiate("Font", font.name, font.style, font.size)
        }

        add(
            "setFont",
            NONE,
            STRING,
            INT,
            INT
        ) { _, args ->
            val (name, style, size) = args

            name as KBString
            style as KBInt
            size as KBInt

            window.setFont(name.value, style.value, size.value)

            KBNone
        }

        add("getStroke", "Font".data) { runtime, _ ->
            val stroke = window.getStroke()

            runtime.instantiate(
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

            width as KBFloat
            cap as KBInt
            join as KBInt
            miterLimit as KBFloat
            dash as KBArray
            dashPhase as KBFloat

            val dashFA = dash.value.asFloatArray()

            window.setStroke(width.value, cap.value, join.value, miterLimit.value, dashFA, dashPhase.value)

            KBNone
        }

        add("translate", NONE, INT, INT) { _, args ->
            val (x, y) = args

            x as KBInt
            y as KBInt

            window.translate(x.value, y.value)

            KBNone
        }

        add("translate", NONE, DOUBLE, DOUBLE) { _, args ->
            val (x, y) = args

            x as KBDouble
            y as KBDouble

            window.translate(x.value, y.value)

            KBNone
        }

        add("rotate", NONE, DOUBLE) { _, args ->
            val (theta) = args

            theta as KBDouble

            window.rotate(theta.value)

            KBNone
        }

        add(
            "rotate",
            NONE,
            DOUBLE,
            DOUBLE,
            DOUBLE
        ) { _, args ->
            val (theta, x, y) = args

            theta as KBDouble
            x as KBDouble
            y as KBDouble

            window.rotate(theta.value, x.value, y.value)

            KBNone
        }

        add("scale", NONE, DOUBLE, DOUBLE) { _, args ->
            val (x, y) = args

            x as KBDouble
            y as KBDouble

            window.scale(x.value, y.value)

            KBNone
        }

        add("shear", NONE, DOUBLE, DOUBLE) { _, args ->
            val (x, y) = args

            x as KBDouble
            y as KBDouble

            window.shear(x.value, y.value)

            KBNone
        }

        add("pushMatrix", NONE) { _, _ ->
            window.pushMatrix()

            KBNone
        }

        add("popMatrix", NONE) { _, _ ->
            window.popMatrix()

            KBNone
        }

        add("clear", NONE) { _, _ ->
            window.clear()

            KBNone
        }

        add(
            "drawLine",
            NONE,
            INT,
            INT,
            INT,
            INT
        ) { _, args ->
            val (xa, ya, xb, yb) = args

            xa as KBInt
            ya as KBInt
            xb as KBInt
            yb as KBInt

            window.drawLine(xa.value, ya.value, xb.value, yb.value)

            KBNone
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

            x as KBInt
            y as KBInt
            width as KBInt
            height as KBInt

            window.drawRect(x.value, y.value, width.value, height.value)

            KBNone
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

            x as KBInt
            y as KBInt
            width as KBInt
            height as KBInt

            window.fillRect(x.value, y.value, width.value, height.value)

            KBNone
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

            x as KBInt
            y as KBInt
            width as KBInt
            height as KBInt

            window.drawOval(x.value, y.value, width.value, height.value)

            KBNone
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

            x as KBInt
            y as KBInt
            width as KBInt
            height as KBInt

            window.fillOval(x.value, y.value, width.value, height.value)

            KBNone
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

            x as KBInt
            y as KBInt
            width as KBInt
            height as KBInt
            arcWidth as KBInt
            arcHeight as KBInt

            window.drawRoundRect(x.value, y.value, width.value, height.value, arcWidth.value, arcHeight.value)

            KBNone
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

            x as KBInt
            y as KBInt
            width as KBInt
            height as KBInt
            arcWidth as KBInt
            arcHeight as KBInt

            window.fillRoundRect(x.value, y.value, width.value, height.value, arcWidth.value, arcHeight.value)

            KBNone
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

            x as KBInt
            y as KBInt
            width as KBInt
            height as KBInt
            raised as KBBool

            window.draw3DRect(x.value, y.value, width.value, height.value, raised.value)

            KBNone
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

            x as KBInt
            y as KBInt
            width as KBInt
            height as KBInt
            raised as KBBool

            window.fill3DRect(x.value, y.value, width.value, height.value, raised.value)

            KBNone
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

            x as KBInt
            y as KBInt
            width as KBInt
            height as KBInt
            startAngle as KBInt
            arcAngle as KBInt

            window.drawArc(x.value, y.value, width.value, height.value, startAngle.value, arcAngle.value)

            KBNone
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

            x as KBInt
            y as KBInt
            width as KBInt
            height as KBInt
            startAngle as KBInt
            arcAngle as KBInt

            window.fillArc(x.value, y.value, width.value, height.value, startAngle.value, arcAngle.value)

            KBNone
        }

        add(
            "drawPolyline",
            NONE,
            INT.array,
            INT.array,
            INT
        ) { _, args ->
            val (xPoints, yPoints, nPoints) = args

            xPoints as KBArray
            yPoints as KBArray
            nPoints as KBInt

            val xPointsIA = xPoints.value.asIntArray()
            val yPointsIA = yPoints.value.asIntArray()

            window.drawPolyline(xPointsIA, yPointsIA, nPoints.value)

            KBNone
        }

        add(
            "drawPolygon",
            NONE,
            INT.array,
            INT.array,
            INT
        ) { _, args ->
            val (xPoints, yPoints, nPoints) = args

            xPoints as KBArray
            yPoints as KBArray
            nPoints as KBInt

            val xPointsIA = xPoints.value.asIntArray()
            val yPointsIA = yPoints.value.asIntArray()

            window.drawPolygon(xPointsIA, yPointsIA, nPoints.value)

            KBNone
        }

        add(
            "fillPolygon",
            NONE,
            INT.array,
            INT.array,
            INT
        ) { _, args ->
            val (xPoints, yPoints, nPoints) = args

            xPoints as KBArray
            yPoints as KBArray
            nPoints as KBInt

            val xPointsIA = xPoints.value.asIntArray()
            val yPointsIA = yPoints.value.asIntArray()

            window.fillPolygon(xPointsIA, yPointsIA, nPoints.value)

            KBNone
        }

        add(
            "drawString",
            NONE,
            STRING,
            INT,
            INT
        ) { _, args ->
            val (s, x, y) = args

            s as KBString
            x as KBInt
            y as KBInt

            window.drawString(s.value, x.value, y.value)

            KBNone
        }

        add("loadImage", "Image".data, STRING) { runtime, args ->
            val (path) = args

            path as KBString

            val (id, width, height) = window.loadImage(path.value)

            runtime.instantiate("Image", id, width, height)
        }

        add(
            "drawImage",
            NONE,
            "Image".data,
            INT,
            INT
        ) { _, args ->
            val (image, x, y) = args

            image as KBData
            x as KBInt
            y as KBInt

            val (id) = image.value.deref()

            id as KBInt

            window.drawImage(id.value, x.value, y.value)

            KBNone
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

            image as KBData
            x as KBInt
            y as KBInt
            width as KBInt
            height as KBInt

            val (id) = image.value.deref()

            id as KBInt

            window.drawImage(id.value, x.value, y.value, width.value, height.value)

            KBNone
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

            image as KBData
            dxa as KBInt
            dya as KBInt
            dxb as KBInt
            dyb as KBInt
            sxa as KBInt
            sya as KBInt
            sxb as KBInt
            syb as KBInt

            val (id) = image.value.deref()

            id as KBInt

            window.drawImage(
                id.value,
                dxa.value,
                dya.value,
                dxb.value,
                dyb.value,
                sxa.value,
                sya.value,
                sxb.value,
                syb.value
            )

            KBNone
        }

        add("flip", NONE) { _, _ ->
            window.flip()

            KBNone
        }

        add("keyIsDown", BOOL, INT) { _, args ->
            val (keyCode) = args

            keyCode as KBInt

            KBBool(window.keyIsDown(keyCode.value))
        }

        add("keyIsHeld", BOOL, INT) { _, args ->
            val (keyCode) = args

            keyCode as KBInt

            KBBool(window.keyIsHeld(keyCode.value))
        }

        add("keyIsUp", BOOL, INT) { _, args ->
            val (keyCode) = args

            keyCode as KBInt

            KBBool(window.keyIsUp(keyCode.value))
        }

        add("buttonIsDown", BOOL, INT) { _, args ->
            val (buttonCode) = args

            buttonCode as KBInt

            KBBool(window.buttonIsDown(buttonCode.value))
        }

        add("buttonIsHeld", BOOL, INT) { _, args ->
            val (buttonCode) = args

            buttonCode as KBInt

            KBBool(window.buttonIsHeld(buttonCode.value))
        }

        add("buttonIsUp", BOOL, INT) { _, args ->
            val (buttonCode) = args

            buttonCode as KBInt

            KBBool(window.buttonIsUp(buttonCode.value))
        }

        add("mouseX", INT) { _, _ -> KBInt(window.mousePoint.x) }

        add("mouseY", INT) { _, _ -> KBInt(window.mousePoint.y) }

        add("mouseWheel", INT) { _, _ -> KBInt(window.mouseWheel) }

        add("poll", NONE) { _, _ ->
            window.poll()

            KBNone
        }
    }

    private fun add(
        name: String,
        returnType: DataType,
        vararg paramTypes: DataType,
        function: (runtime: Runtime, args: List<KBV>) -> KBV,
    ) {
        val key = paramTypes.joinToString(prefix = "${name.lowercase()}(", separator = ",", postfix = ")")

        builtins[key] = Builtin(returnType, function)
    }

    operator fun get(sub: Stmt.Sub) =
        builtins[sub.signature]

    class Builtin(private val returnType: DataType, val function: (runtime: Runtime, args: List<KBV>) -> KBV) {
        operator fun invoke(runtime: Runtime, args: List<KBV>): KBV? {
            val result = function(runtime, args)

            return returnType.filter(runtime, result)
        }
    }
}

private operator fun <E> List<E>.component6() = this[5]
private operator fun <E> List<E>.component7() = this[6]
private operator fun <E> List<E>.component8() = this[7]
private operator fun <E> List<E>.component9() = this[8]
