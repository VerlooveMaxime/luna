package game.idle.flow

/**
 * How much of its work a step does before the next one starts: a count written before the resource
 * (`chop 5 oak`), or, without one, as much as it can (until the inventory is full, or nothing is left to use).
 */
object StepAmount {

    const val MAX = 1000

    /** The counts the builder's amount field offers after the step's own word for "as much as it can". */
    val COUNTS = listOf("1", "5", "10")

    /** The count a line starts with and the words after it; no count when the first word is not a number. */
    fun split(words: List<String>): Pair<Int?, List<String>> {
        val count = words.firstOrNull()?.toIntOrNull() ?: return null to words
        if (count !in 1..MAX) throw FlowError("A step's count is 1 to $MAX, not $count")
        return count to words.drop(1)
    }

    /** The count a field value stands for; its word for "as much as it can" stands for none. */
    fun count(value: String): Int? = value.toIntOrNull()

    /** The field value for [count], or [unbounded] for none. */
    fun value(count: Int?, unbounded: String): String = count?.toString() ?: unbounded

    /** The count as a line writes it, a word and a space, or nothing for none. */
    fun prefix(value: String): String = count(value)?.let { "$it " } ?: ""
}
