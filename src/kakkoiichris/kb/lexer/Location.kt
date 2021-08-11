package kakkoiichris.kb.lexer

data class Location(val name: String, val row: Int, val col: Int) {
    companion object {
        val none = Location("", 0, 0)
    }
    
    override fun toString() =
        "$name.kb @ $row, $col"
}