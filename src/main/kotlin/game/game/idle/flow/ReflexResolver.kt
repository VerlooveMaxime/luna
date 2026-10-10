package game.idle.flow

import game.idle.flow.ReflexKeys.BELOW
import game.idle.flow.ReflexKeys.BELOW_RANGE
import game.idle.flow.ReflexKeys.BELOW_RULE
import game.idle.flow.ReflexKeys.DEFAULT_BELOW
import game.idle.flow.ReflexKeys.DO
import game.idle.flow.ReflexKeys.EAT
import game.idle.flow.ReflexKeys.FOODS
import game.idle.flow.ReflexKeys.JUMP
import game.idle.flow.ReflexKeys.RUN_AWAY
import game.idle.flow.ReflexKeys.STEP
import game.idle.flow.ReflexKeys.STOP
import game.idle.flow.ReflexKeys.THEN

/**
 * Checks a flow's reflexes against its steps (S07c): a jump becomes the index of the step whose id it holds, so it
 * follows that step wherever it moved, and a step that was deleted makes the reflex unable to work. [portions] maps
 * every item id Luna can eat to the ids of its food's every portion, so a reflex picking a cake eats its slices too.
 */
class ReflexResolver(private val portions: Map<Int, Set<Int>>) {

    /**
     * For each of [steps], the reflexes attached to it resolved, in its order; throws [FlowError] naming the first
     * reflex that cannot work, attached or not, or a step attached to a reflex the flow does not hold.
     */
    fun resolve(steps: List<StepSettings>, reflexes: List<ReflexSettings>): List<List<ResolvedReflex>> {
        val resolved = outcomes(steps, reflexes).mapIndexed { index, outcome ->
            outcome.getOrElse { e -> throw FlowError("Reflex ${index + 1}: ${e.message}") }
        }
        val byId = reflexes.map { it.id }.zip(resolved).toMap()
        return steps.mapIndexed { index, step ->
            step.reflexes.map { id -> byId[id] ?: throw FlowError("Step ${index + 1}: attached to a reflex the flow does not hold") }
        }
    }

    /** Why each of [reflexes] cannot work, null for one that can. */
    fun problems(steps: List<StepSettings>, reflexes: List<ReflexSettings>): List<String?> =
        outcomes(steps, reflexes).map { it.exceptionOrNull()?.message }

    private fun outcomes(steps: List<StepSettings>, reflexes: List<ReflexSettings>): List<Result<ResolvedReflex>> =
        reflexes.mapIndexed { index, reflex ->
            try {
                Result.success(ResolvedReflex(index + 1, trigger(reflex), action(reflex, steps)))
            } catch (e: FlowError) {
                Result.failure(e)
            }
        }

    private fun trigger(reflex: ReflexSettings): ReflexTrigger {
        val text = reflex[BELOW] ?: return ReflexTrigger.HitpointsBelow(DEFAULT_BELOW)
        val percent = text.toIntOrNull()?.takeIf { it in BELOW_RANGE } ?: throw FlowError("$BELOW_RULE, not '$text'")
        return ReflexTrigger.HitpointsBelow(percent)
    }

    private fun action(reflex: ReflexSettings, steps: List<StepSettings>): ReflexAction =
        when (val what = reflex[DO] ?: EAT) {
            EAT -> ReflexAction.Eat(foods(reflex))
            RUN_AWAY -> ReflexAction.RunAway(then(reflex, steps))
            else -> throw FlowError("'$what' is not something a reflex does")
        }

    private fun foods(reflex: ReflexSettings): Set<Int> =
        StepItems.parse(reflex[FOODS]).flatMap { portions[it.id] ?: throw FlowError("item ${it.id} is not something you can eat") }.toSet()

    private fun then(reflex: ReflexSettings, steps: List<StepSettings>): ReflexThen =
        when (val then = reflex[THEN] ?: STOP) {
            STOP -> ReflexThen.StopFlow
            JUMP -> ReflexThen.JumpTo(jumpTarget(reflex[STEP], steps))
            else -> throw FlowError("'$then' is not what a reflex does after running away")
        }

    private fun jumpTarget(step: String?, steps: List<StepSettings>): Int {
        val id = step?.toIntOrNull() ?: throw FlowError("pick the step to jump to")
        return steps.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: throw FlowError("jumps to a step that was deleted")
    }
}
