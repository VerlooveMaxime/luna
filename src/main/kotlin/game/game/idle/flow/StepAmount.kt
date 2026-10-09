package game.idle.flow

/**
 * How much of its work a step does before the next one starts: a count kept under [KEY], or, without one, as much as
 * it can (until the inventory is full, or nothing is left to use).
 */
object StepAmount {

    const val KEY = "amount"

    const val MAX = 1000

    const val RULE = "the amount takes 1 to $MAX"

    /**
     * The configure screen's amount under [label]: "5 per lap", or [unbounded], the step's words for as much as it can,
     * which the button worded [button] sets.
     */
    fun field(label: String, unbounded: String, button: String): StepField.Typed =
        StepField.Typed(KEY, label, 1..MAX, RULE, shown = { it?.let { count -> "$count per lap" } ?: unbounded }, unbounded = button)

    /** The count [settings] hold, null for none; throws [FlowError] when it is not a count from 1 to [MAX]. */
    fun read(settings: StepSettings): Int? {
        val text = settings[KEY] ?: return null
        val count = text.toIntOrNull()
        if (count == null || count !in 1..MAX) throw FlowError("$RULE, not '$text'")
        return count
    }

    /** The builder's slot line: "5 per lap" ("5 kills per lap" with [counted] "kills"), or [unbounded] without a count. */
    fun detail(settings: StepSettings, unbounded: String, counted: String = ""): String =
        settings[KEY]?.let { count -> if (counted.isEmpty()) "$count per lap" else "$count $counted per lap" } ?: unbounded

    /** The count as a summary writes it before what the step works on: a number and a space, or nothing. */
    fun prefix(settings: StepSettings): String = settings[KEY]?.let { "$it " } ?: ""
}
