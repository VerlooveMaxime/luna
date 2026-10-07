package game.idle.flow

/** Checks a whole flow set from outside the builder (the agent harness) the way the builder checks its edits. */
class FlowCheck(val resolver: FlowResolver, private val maxSteps: Int) {

    /** Throws [FlowError] when [steps] are too many or one of them is wrong. */
    fun check(steps: List<StepSettings>) {
        if (steps.size > maxSteps) throw FlowError("A flow holds $maxSteps steps at most, not ${steps.size}")
        resolver.resolve(steps)
    }
}
