package game.idle.flow

import game.idle.location.Area

/** How far from its work spot a step looks for what it works on, in tiles, kept under [KEY]. */
object StepRadius {

    const val KEY = "within"

    const val DEFAULT = 10

    const val RULE = "within takes 1 to ${Area.MAX_RADIUS} tiles"

    /** The configure screen's Within, on the right where the work spot's settings go (Maxime, 2026-10-09). */
    fun field(): StepField.Typed =
        StepField.Typed(KEY, "Within", 1..Area.MAX_RADIUS, RULE, shown = { "${it ?: DEFAULT} tiles" }, column = FieldColumn.RIGHT)

    /** The radius [settings] hold, [DEFAULT] without one; throws [FlowError] when it is out of range. */
    fun read(settings: StepSettings): Int {
        val text = settings[KEY] ?: return DEFAULT
        val radius = text.toIntOrNull()
        if (radius == null || radius !in 1..Area.MAX_RADIUS) {
            throw FlowError("$RULE, not '$text'")
        }
        return radius
    }

    /** The end of a summary: nothing for the default. */
    fun suffix(settings: StepSettings): String =
        settings[KEY]?.takeIf { it != DEFAULT.toString() }?.let { " within $it" } ?: ""
}
