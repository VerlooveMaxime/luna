package game.idle.flow

/**
 * Checks a whole flow set from outside the builder (the agent harness): within the step limit, every step resolving.
 * Stricter than the builder, which keeps a step that cannot work and shows why on its slot (Maxime, 2026-10-09).
 */
class FlowCheck(val resolver: FlowResolver, private val maxSteps: Int) {

    /** Throws [FlowError] when [steps] are too many or one of them is wrong. */
    fun check(steps: List<StepSettings>) {
        if (steps.size > maxSteps) throw FlowError("A flow holds $maxSteps steps at most, not ${steps.size}")
        resolver.resolve(steps)
    }
}
