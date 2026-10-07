package game.idle.flow

import game.idle.location.Area

/** How far from its work spot a step looks for what it works on, in tiles, kept under [KEY]. */
object StepRadius {

    const val KEY = "within"

    const val DEFAULT = 10

    /** The radii the builder cycles through; a step may hold any from 1 to [Area.MAX_RADIUS]. */
    val CHOICES = listOf(5, 10, 15, 20, 30).map { it.toString() }

    fun field(): StepField.Choice = StepField.Choice(KEY, "within (tiles)", default = DEFAULT.toString()) { CHOICES }

    /** The radius [settings] hold, [DEFAULT] without one; throws [FlowError] when it is out of range. */
    fun read(settings: StepSettings): Int {
        val text = settings[KEY] ?: return DEFAULT
        val radius = text.toIntOrNull()
        if (radius == null || radius !in 1..Area.MAX_RADIUS) {
            throw FlowError("within takes a number of tiles from 1 to ${Area.MAX_RADIUS}, not '$text'")
        }
        return radius
    }

    /** The end of a summary: nothing for the default. */
    fun suffix(settings: StepSettings): String =
        settings[KEY]?.takeIf { it != DEFAULT.toString() }?.let { " within $it" } ?: ""
}
