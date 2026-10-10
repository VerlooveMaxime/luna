package game.idle.flow

/**
 * How much of its work a step does before the next one starts: a count kept under [KEY], or, without one, as much as
 * it can (until the inventory is full, or nothing is left to use). A count has no cap but what the client's "Enter
 * amount" takes (Maxime, 2026-10-10).
 */
object StepAmount {

    const val KEY = "amount"

    /** Every count from 1 up, as a withdrawal's amount too. */
    val RANGE = 1..Int.MAX_VALUE

    const val RULE = "the amount takes 1 or more"

    /**
     * The configure screen's amount under [label]: "5 per lap", or [unbounded], the step's words for as much as it can,
     * which the button worded [button] sets.
     */
    fun field(label: String, unbounded: String, button: String): StepField.Typed =
        StepField.Typed(KEY, label, RANGE, RULE, shown = { it?.let { count -> "${short(count)} per lap" } ?: unbounded }, unbounded = button)

    /** The count [settings] hold, null for none; throws [FlowError] when it is not a count of 1 or more. */
    fun read(settings: StepSettings): Int? {
        val text = settings[KEY] ?: return null
        val count = text.toIntOrNull()
        if (count == null || count !in RANGE) throw FlowError("$RULE, not '$text'")
        return count
    }

    /** The builder's slot line: "5 per lap" ("5 kills per lap" with [counted] "kills"), or [unbounded] without a count. */
    fun detail(settings: StepSettings, unbounded: String, counted: String = ""): String =
        settings[KEY]?.let { count -> if (counted.isEmpty()) "${short(count)} per lap" else "${short(count)} $counted per lap" } ?: unbounded

    /**
     * A count as the 377 shows an item stack, so a big one fits a field: as it is below 100,000, then in thousands
     * ("150K"), from ten million in millions ("10M"). A text that is not a count shows as it is.
     */
    fun short(count: String): String {
        val number = count.toIntOrNull() ?: return count
        return when {
            number < 100_000 -> count
            number < 10_000_000 -> "${number / 1000}K"
            else -> "${number / 1_000_000}M"
        }
    }

    fun short(count: Int): String = short(count.toString())

    /** The count as a summary writes it before what the step works on: a number and a space, or nothing. */
    fun prefix(settings: StepSettings): String = settings[KEY]?.let { "$it " } ?: ""
}
