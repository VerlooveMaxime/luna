package game.idle.flow

/**
 * The builder's reasons in one form (Maxime, 2026-10-11, Q13): what a step's slot and configure band show in red when
 * it cannot work, and what the chat box says of a number out of range, naming the row the player fills in ("Needs a
 * tree" under the row Tree), in the run-time reasons' form ([StepWords]): sentence case, no period, no kind word, on
 * at most two of the slot's lines. The kinds raise them as [FlowError]s.
 */
object Reasons {

    /** "Needs a tree" for the row Tree, "Needs logs" for a row holding several ([countable] false). */
    fun needs(label: String, countable: Boolean = true): String = "Needs ${thing(label, countable)}"

    /** "'palm' is not a tree" for the row Tree, "'1511' is not food" ([countable] false). */
    fun notA(label: String, text: String, countable: Boolean = true): String = "'$text' is not ${thing(label, countable)}"

    /** The rule a row's number follows: "Within takes 1 to 32 tiles", "Kills take 1 or more" (a plural row). */
    fun takes(label: String, range: String): String = "$label ${if (label.endsWith("s")) "take" else "takes"} $range"

    /** The rule and the text that broke it: "Within takes 1 to 32 tiles, not '40'". */
    fun takes(label: String, range: String, text: String): String = "${takes(label, range)}, not '$text'"

    /**
     * "an" before a vowel, or before a letter said by its name when the first word is spelt out ("an npc to fight");
     * else "a".
     */
    fun article(words: String): String {
        val word = words.substringBefore(' ')
        val first = word.first()
        val spelt = word.none { it in VOWELS || it == 'y' }
        return if (first in VOWELS || spelt && first in SPELT_WITH_VOWEL) "an" else "a"
    }

    /** "a tree", "an npc", "logs". */
    private fun thing(label: String, countable: Boolean): String {
        val word = label.lowercase()
        return if (countable) "${article(word)} $word" else word
    }

    private const val VOWELS = "aeiou"

    /** The letters whose names start with a vowel sound. */
    private const val SPELT_WITH_VOWEL = "fhlmnrsx"
}
