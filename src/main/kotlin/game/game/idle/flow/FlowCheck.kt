package game.idle.flow

/**
 * Checks a whole flow set from outside the builder (the agent harness): within the step and reflex limits, ids unique,
 * every step and reflex resolving. Stricter than the builder, which keeps a step that cannot work and shows why on its
 * slot (Maxime, 2026-10-09).
 */
class FlowCheck(val resolver: FlowResolver, private val reflexes: ReflexResolver, private val maxSteps: Int, private val maxReflexes: Int) {

    /** Throws [FlowError] when [steps] or [flowReflexes] are too many, share an id or one of them is wrong. */
    fun check(steps: List<StepSettings>, flowReflexes: List<ReflexSettings>) {
        if (steps.size > maxSteps) throw FlowError("A flow holds $maxSteps steps at most, not ${steps.size}")
        if (flowReflexes.size > maxReflexes) throw FlowError("A flow holds $maxReflexes reflexes at most, not ${flowReflexes.size}")
        unique("steps", steps.map { it.id })
        unique("reflexes", flowReflexes.map { it.id })
        resolver.resolve(steps)
        reflexes.resolve(steps, flowReflexes)
    }

    private fun unique(what: String, ids: List<Int>) {
        val shared = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.firstOrNull() ?: return
        throw FlowError("Two of the flow's $what have id $shared")
    }
}
