package kakkoiichris.kb

import kakkoiichris.kb.script.Redirect
import kakkoiichris.kb.script.Script
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
    println("""
        KakkoiiBasic REPL
        Copyright ${Typography.copyright} 2021, KakkoiiChris
    
    """.trimIndent())
    
    print("> ")
    
    val text = readLine() ?: ""
    
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
    catch (e: Redirect.Yield) {
        e.value
    }
    catch (_: Redirect.Return) {
    }
    
    val end = System.nanoTime()
    
    if (value === Unit) {
        println("\nFinished in ${(end - start) / 1E9} seconds!")
    }
    else {
        println("\nFinished in ${(end - start) / 1E9} seconds! Yielded '$value'!")
    }
}