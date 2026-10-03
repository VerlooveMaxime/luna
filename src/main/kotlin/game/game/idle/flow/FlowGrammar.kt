package game.idle.flow

/**
 * The step kinds a flow can use, one line per step; after the last step the flow starts over. Words are matched
 * ignoring case and extra spaces.
 */
class FlowGrammar(val types: List<StepType>) {

    init {
        require(types.isNotEmpty()) { "A grammar needs at least one kind of step" }
        require(types.map { it.keyword }.toSet().size == types.size) { "Two kinds of step share a keyword" }
    }

    val help: String = types.joinToString(", ") { it.usage }

    fun parse(line: String): FlowStep {
        val words = line.trim().split(WHITESPACE).filter { it.isNotEmpty() }.map { it.lowercase() }
        val type = types.firstOrNull { it.keyword == words.firstOrNull() }
            ?: throw FlowError("Unknown step '$line'. Steps: $help")
        return FlowStep(type, type.parse(words.drop(1)))
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
