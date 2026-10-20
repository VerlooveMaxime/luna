package game.harness

import io.luna.game.model.Position

/**
 * The tile a `move x y [z]` command puts the player on, read as Luna reads a typed command (lower case, split on single
 * spaces) and `::move` its numbers: the player's [floor] unless exactly three are given. Null for another command, or a
 * move Luna would not carry out.
 */
fun moveTarget(text: String, floor: Int): Position? {
    val command = text.trim().removePrefix("::").trim().lowercase()
    val name = command.substringBefore(' ')
    if (name != "move" || name == command) return null
    val numbers = command.substringAfter(' ').split(" ").map(String::toIntOrNull)
    val x = numbers.getOrNull(0) ?: return null
    val y = numbers.getOrNull(1) ?: return null
    val z = if (numbers.size == 3) numbers[2] ?: return null else floor
    return Position(x, y, z)
}
