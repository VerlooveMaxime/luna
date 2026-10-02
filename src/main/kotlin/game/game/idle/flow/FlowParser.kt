package game.idle.flow

/**
 * Grammar, one step per line; after the last step the flow starts over:
 *
 *     chop <tree> @<location>     ends when the inventory is full
 *     drop                        drops what the chop steps before it gathered
 *     bank deposit all
 */
object FlowParser {

    const val HELP = "chop <tree> @<location>, drop, bank deposit all"

    fun parse(line: String): FlowStep {
        val words = line.trim().split(WHITESPACE).filter { it.isNotEmpty() }.map { it.lowercase() }
        return when (words.firstOrNull()) {
            "chop" -> chop(words.drop(1))
            "drop" -> drop(words.drop(1))
            "bank" -> bank(words.drop(1))
            else -> throw FlowError("Unknown step '$line'. Steps: $HELP")
        }
    }

    private fun chop(words: List<String>): FlowStep.Chop {
        val tree = words.firstOrNull() ?: throw FlowError("chop needs a tree: chop <tree> @<location>")
        if (',' in tree) throw FlowError("One kind of tree per chop step: chop <tree> @<location>")
        val at = words.getOrNull(1)
        if (at == null || !at.startsWith("@") || at.length == 1) throw FlowError("chop needs a location: chop $tree @<location>")
        val rest = words.drop(2)
        if (rest.isNotEmpty()) {
            throw FlowError(
                "Unexpected '${rest.joinToString(" ")}' after the location. A chop step ends when the inventory is full; " +
                    "'drop' and 'bank deposit all' are steps of their own, and the flow repeats by itself.",
            )
        }
        return FlowStep.Chop(tree, at.substring(1))
    }

    private fun drop(words: List<String>): FlowStep {
        if (words.isNotEmpty()) throw FlowError("drop takes nothing after it: it drops what the chop steps before it gathered")
        return FlowStep.Drop
    }

    private fun bank(words: List<String>): FlowStep {
        if (words != listOf("deposit", "all")) throw FlowError("The only bank step is 'bank deposit all'")
        return FlowStep.BankDepositAll
    }

    private val WHITESPACE = Regex("\\s+")
}
