package kakkoiichris.kb.util

class Stack<X> {
    private val elements = mutableListOf<X>()
    
    fun peek() =
        if (elements.isNotEmpty())
            elements.last()
        else
            null
    
    fun push(x: X) {
        elements.add(x)
    }
    
    fun pop() {
        elements.remove(peek())
    }
}