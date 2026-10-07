package game.idle.flow

/** Checks a flow's steps against the data, each step knowing what the steps before it set up. */
class FlowResolver(val types: StepTypes) {

    /** Throws [FlowError] naming the first step that is wrong. */
    fun resolve(steps: List<StepSettings>): List<ResolvedStep> {
        var context = FlowContext()
        return steps.mapIndexed { index, settings ->
            val step = try {
                val type = types.find(settings.kind) ?: throw FlowError("'${settings.kind}' is not a kind of step")
                type.resolve(settings, context)
            } catch (e: FlowError) {
                throw FlowError("Step ${index + 1}: ${e.message}")
            }
            context = step.after(context)
            step
        }
    }
}
