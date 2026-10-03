package game.idle.flow

/** Checks flow lines against the data, each step knowing what the steps before it set up. */
class FlowResolver(val grammar: FlowGrammar) {

    /** Throws [FlowError] naming the first line that is wrong. */
    fun resolve(lines: List<String>): List<ResolvedStep> {
        var context = FlowContext()
        return lines.mapIndexed { index, line ->
            val step = try {
                val parsed = grammar.parse(line)
                parsed.type.resolve(parsed.values, context)
            } catch (e: FlowError) {
                throw FlowError("Step ${index + 1}: ${e.message}")
            }
            context = step.after(context)
            step
        }
    }
}
