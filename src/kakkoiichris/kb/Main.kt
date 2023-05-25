package kakkoiichris.kb

import kakkoiichris.kb.script.Redirect
import kakkoiichris.kb.script.Script
import kakkoiichris.kb.util.KBError
import kakkoiichris.kb.util.Source
import java.io.File

fun main(args: Array<String>) {
    when (args.size) {
        0    -> repl()
        
        1    -> file(args[0])
        
        else -> error("usage: kbasic (filePath)?")
    }
}

private fun repl() {
    println(
        """
        KakkoiiBasic REPL
        Copyright ${Typography.copyright} 2021, KakkoiiChris
    
    """.trimIndent()
    )
    
    print("> ")
    
    val text = readln()
    
    println()
    
    exec("<REPL>", text)
}

private fun file(filePath: String) {
    val file = File(filePath)
    
    val name = file.nameWithoutExtension
    
    val text = file.readText()
    
    exec(name, text)
}

private fun exec(name: String, text: String) {
    val source = Source(name, text)
    
    val stmts = source.compile()
    
    val script = Script(stmts)
    
    val start = System.nanoTime()
    
    val value = try {
        script.run()
    }
    catch (r: Redirect.Yield) {
        r.value
    }
    catch (_: Redirect.Return) {
    }
    catch (e: KBError) {
        System.err.println(e.kbStackTrace)
        
        e.printStackTrace()
    }
    
    val end = System.nanoTime()
    
    val seconds = (end - start) / 1E9
    
    val result = if (value === Unit) "" else " Yielded '$value'!"
    
    println("\nFinished in $seconds seconds!$result")
}