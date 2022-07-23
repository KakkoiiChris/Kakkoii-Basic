package kakkoiichris.kb.util

class Stack<X> {
    private val elements = mutableListOf<X>()
    
    val isEmpty get() = elements.isEmpty()
    
    fun peek() =
        if (elements.isNotEmpty())
            elements.last()
        else
            null
    
    fun push(x: X): X {
        elements.add(x)
        
        return x
    }
    
    fun pop(): X? {
        val element = peek()
        
        elements.removeLast()
        
        return element
    }
}