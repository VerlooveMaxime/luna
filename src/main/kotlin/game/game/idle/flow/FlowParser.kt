package game.idle.flow

/**
 * Grammar, one step per line:
 *
 *     chop <tree>[,<tree>...] @<location> [drop] [until inventory full | until <n> logs | until level <n>]
 *     bank deposit all
 *     loop
 */
object FlowParser {

    const val HELP = "chop <tree> @<location> [drop] [until inventory full | until <n> logs | until level <n>], " +
        "bank deposit all, loop"

    fun parse(line: String): FlowStep {
        val words = line.trim().split(WHITESPACE).filter { it.isNotEmpty() }.map { it.lowercase() }
        return when (words.firstOrNull()) {
            "chop" -> chop(words.drop(1))
            "bank" -> bank(words.drop(1))
            "loop" -> loop(words.drop(1))
            else -> throw FlowError("Unknown step '$line'. Steps: $HELP")
        }
    }

    private fun chop(words: List<String>): FlowStep.Chop {
        val trees = words.firstOrNull()?.split(',')?.filter { it.isNotEmpty() }.orEmpty()
        if (trees.isEmpty()) throw FlowError("chop needs a tree: chop <tree> @<location>")
        val at = words.getOrNull(1)
        if (at == null || !at.startsWith("@") || at.length == 1) throw FlowError("chop needs a location: chop ${words[0]} @<location>")
        var rest = words.drop(2)
        val drop = rest.firstOrNull() == "drop"
        if (drop) rest = rest.drop(1)
        val until = when {
            rest.isEmpty() -> null
            rest.first() == "until" -> until(rest.drop(1))
            else -> throw FlowError("Unexpected '${rest.joinToString(" ")}' after the location. Expected 'drop' or 'until'.")
        }
        return FlowStep.Chop(trees, at.substring(1), drop, until)
    }

    private fun until(words: List<String>): Until =
        when {
            words == listOf("inventory", "full") -> Until.InventoryFull
            words.size == 2 && words[1] == "logs" -> Until.Logs(positive(words[0], "logs"))
            words.size == 2 && words[0] == "level" -> Until.Level(level(words[1]))
            else -> throw FlowError("Unknown condition 'until ${words.joinToString(" ")}'. Conditions: inventory full, <n> logs, level <n>")
        }

    private fun positive(word: String, what: String): Int =
        word.toIntOrNull()?.takeIf { it > 0 } ?: throw FlowError("'$word' is not a number of $what")

    private fun level(word: String): Int =
        word.toIntOrNull()?.takeIf { it in 1..99 } ?: throw FlowError("'$word' is not a level from 1 to 99")

    private fun bank(words: List<String>): FlowStep {
        if (words != listOf("deposit", "all")) throw FlowError("The only bank step is 'bank deposit all'")
        return FlowStep.BankDepositAll
    }

    private fun loop(words: List<String>): FlowStep {
        if (words.isNotEmpty()) throw FlowError("loop takes nothing after it")
        return FlowStep.Loop
    }

    private val WHITESPACE = Regex("\\s+")
}
