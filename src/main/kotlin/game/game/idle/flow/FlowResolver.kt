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
            context = after(step, context, number = index + 1)
            step
        }
    }

    /**
     * Why each step cannot work, null for one that can: each checked against what the steps before it set up, a step
     * that does not resolve adding nothing, as [contextBefore] does. Unlike [resolve], every step gets its answer.
     */
    fun problems(steps: List<StepSettings>): List<String?> {
        var context = FlowContext()
        return steps.mapIndexed { index, settings ->
            try {
                val type = types.find(settings.kind) ?: throw FlowError("'${settings.kind}' is not a kind of step")
                val step = type.resolve(settings, context)
                context = after(step, context, number = index + 1)
                null
            } catch (e: FlowError) {
                e.message
            }
        }
    }

    /** What the steps before the one at [index] set up, for its options; a step that does not resolve adds nothing. */
    fun contextBefore(steps: List<StepSettings>, index: Int): FlowContext =
        steps.take(index).foldIndexed(FlowContext()) { before, context, settings ->
            resolvedOrNull(settings, context)?.let { after(it, context, number = before + 1) } ?: context
        }

    /** [context] once [step], the [number]th of the flow, has run: what it newly gets is known to come from it. */
    private fun after(step: ResolvedStep, context: FlowContext, number: Int): FlowContext {
        val next = step.after(context)
        return next.copy(gatheredBy = context.gatheredBy + (next.gathered - context.gathered).associateWith { number })
    }

    private fun resolvedOrNull(settings: StepSettings, context: FlowContext): ResolvedStep? =
        try {
            types.find(settings.kind)?.resolve(settings, context)
        } catch (e: FlowError) {
            null
        }
}
