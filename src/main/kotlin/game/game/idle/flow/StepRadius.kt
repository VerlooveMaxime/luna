package game.idle.flow

import game.idle.location.Area

/** How far from its work spot a step looks for what it works on: `within <r>` at the end of its line. */
object StepRadius {

    const val DEFAULT = 10

    /** The radii the builder cycles through; a typed line may use any from 1 to [Area.MAX_RADIUS]. */
    val CHOICES = listOf(5, 10, 15, 20, 30).map { it.toString() }

    fun field(): StepField.Choice = StepField.Choice("within (tiles)", default = DEFAULT.toString()) { CHOICES }

    /** The radius the last words of a line give: none for the default, or `within <r>`; [usage] goes in the error. */
    fun parse(words: List<String>, after: String, usage: String): Int = when {
        words.isEmpty() -> DEFAULT
        words.size == 2 && words[0] == "within" -> check(words[1])
        else -> throw FlowError("Unexpected '${words.joinToString(" ")}' after the $after: $usage")
    }

    fun check(text: String): Int {
        val radius = text.toIntOrNull()
        if (radius == null || radius !in 1..Area.MAX_RADIUS) {
            throw FlowError("within takes a number of tiles from 1 to ${Area.MAX_RADIUS}, not '$text'")
        }
        return radius
    }

    /** The end of a line for the field value: nothing for the default. */
    fun suffix(value: String): String = if (value == DEFAULT.toString()) "" else " within $value"
}
