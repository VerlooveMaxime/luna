package game.idle.flow

import game.idle.location.Tile

/** A tile as a setting keeps it ([Tile.text]: `x y`, or `x y floor`), read back for the walk step and work spots. */
object TileText {

    /** The tile [text] holds; throws [FlowError] naming [what] needs it when it is not one. */
    fun read(text: String, what: String): Tile {
        val words = text.split(" ").filter { it.isNotEmpty() }
        val numbers = words.mapNotNull { it.toIntOrNull() }
        if (words.size !in 2..3 || numbers.size != words.size) {
            throw FlowError("$what needs a tile: x y, or x y floor, not '${words.joinToString(" ")}'")
        }
        val (x, y) = numbers
        val z = numbers.getOrNull(2) ?: 0
        if (x < 0 || y < 0) throw FlowError("A tile has no negative coordinates: $x $y")
        if (z !in 0..3) throw FlowError("The floor is 0 to 3, not $z")
        return Tile(x, y, z)
    }
}
