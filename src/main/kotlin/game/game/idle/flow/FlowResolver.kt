package game.idle.flow

/**
 * Checks a flow's steps against the data, each step knowing what the steps before it set up and what the whole flow
 * gathers or makes ([FlowContext.lap]).
 */
class FlowResolver(val types: StepTypes) {

    /** Throws [FlowError] naming the first step that is wrong. */
    fun resolve(steps: List<StepSettings>): List<ResolvedStep> =
        outcomes(steps).mapIndexed { index, outcome ->
            outcome.getOrElse { e -> throw FlowError("Step ${index + 1}: ${e.message}") }
        }

    /**
     * Why each step cannot work, null for one that can: each checked against what the steps before it set up, a step
     * that does not resolve adding nothing, as [contextBefore] does. Unlike [resolve], every step gets its answer.
     */
    fun problems(steps: List<StepSettings>): List<String?> = outcomes(steps).map { it.exceptionOrNull()?.message }

    /** Each step resolved, null for one that cannot work, as [problems] checks them. */
    fun resolvedEach(steps: List<StepSettings>): List<ResolvedStep?> = outcomes(steps).map { it.getOrNull() }

    /** What the steps before the one at [index] set up, for its options; a step that does not resolve adds nothing. */
    fun contextBefore(steps: List<StepSettings>, index: Int): FlowContext =
        steps.take(index).foldIndexed(FlowContext(lap = lap(steps))) { before, context, settings ->
            resolvedOrNull(settings, context)?.let { after(it, context, number = before + 1) } ?: context
        }

    /** Each step's outcome against what the steps before it set up; a [FlowError] is the only failure kept. */
    private fun outcomes(steps: List<StepSettings>): List<Result<ResolvedStep>> {
        var context = FlowContext(lap = lap(steps))
        return steps.mapIndexed { index, settings ->
            try {
                val type = types.find(settings.kind) ?: throw FlowError("'${settings.kind}' is not a kind of step")
                val step = type.resolve(settings, context)
                context = after(step, context, number = index + 1)
                Result.success(step)
            } catch (e: FlowError) {
                Result.failure(e)
            }
        }
    }

    /** What every step of [steps] gathers or makes, each resolved without knowing it (a bank step adds nothing). */
    private fun lap(steps: List<StepSettings>): Set<Int> =
        steps.fold(FlowContext()) { context, settings -> resolvedOrNull(settings, context)?.after(context) ?: context }.gathered

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
