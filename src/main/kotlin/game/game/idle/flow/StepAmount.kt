package game.idle.flow

/**
 * How much of its work a step does before the next one starts: a count kept under [KEY], or, without one, as much as
 * it can (until the inventory is full, or nothing is left to use).
 */
object StepAmount {

    const val KEY = "amount"

    const val MAX = 1000

    /** The counts the builder's amount field offers after the step's own word for "as much as it can". */
    val COUNTS = listOf("1", "5", "10")

    /** The builder's amount field; no count shows as [unbounded], the step's word for "as much as it can". */
    fun field(unbounded: String): StepField.Choice =
        StepField.Choice(KEY, "amount", display = { it.ifEmpty { unbounded } }) { listOf("") + COUNTS }

    /** The count [settings] hold, null for none; throws [FlowError] when it is not a count from 1 to [MAX]. */
    fun read(settings: StepSettings): Int? {
        val text = settings[KEY] ?: return null
        val count = text.toIntOrNull()
        if (count == null || count !in 1..MAX) throw FlowError("A step's count is 1 to $MAX, not '$text'")
        return count
    }

    /** The builder's slot line: "5 per lap" ("5 kills per lap" with [counted] "kills"), or [unbounded] without a count. */
    fun detail(settings: StepSettings, unbounded: String, counted: String = ""): String =
        settings[KEY]?.let { count -> if (counted.isEmpty()) "$count per lap" else "$count $counted per lap" } ?: unbounded

    /** The count as a summary writes it before what the step works on: a number and a space, or nothing. */
    fun prefix(settings: StepSettings): String = settings[KEY]?.let { "$it " } ?: ""
}
