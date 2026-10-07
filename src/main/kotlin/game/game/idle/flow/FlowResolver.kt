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

    /** What the steps before the one at [index] set up, for its options; a step that does not resolve adds nothing. */
    fun contextBefore(steps: List<StepSettings>, index: Int): FlowContext =
        steps.take(index).fold(FlowContext()) { context, settings ->
            resolvedOrNull(settings, context)?.after(context) ?: context
        }

    private fun resolvedOrNull(settings: StepSettings, context: FlowContext): ResolvedStep? =
        try {
            types.find(settings.kind)?.resolve(settings, context)
        } catch (e: FlowError) {
            null
        }
}
