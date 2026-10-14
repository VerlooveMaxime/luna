package game.idle.flow

import game.idle.location.Tile

/** A tile as a setting keeps it ([Tile.text]: `x y`, or `x y floor`), read back for the walk step and work spots. */
object TileText {

    /**
     * The tile [text] holds; throws [FlowError] naming the row [label] when it is not one. The reason names no format:
     * it would not fit the slot, and a tile picked on the map is always one.
     */
    fun read(text: String, label: String): Tile {
        val words = text.split(" ").filter { it.isNotEmpty() }
        val numbers = words.mapNotNull { it.toIntOrNull() }
        if (words.size !in 2..3 || numbers.size != words.size) throw notA(label, words)
        val (x, y) = numbers
        val z = numbers.getOrNull(2) ?: 0
        if (x < 0 || y < 0 || z !in 0..3) throw notA(label, words)
        return Tile(x, y, z)
    }

    private fun notA(label: String, words: List<String>): FlowError = FlowError(Reasons.notA(label, words.joinToString(" ")))
}
