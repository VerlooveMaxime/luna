package game.idle.flow

import game.idle.flow.ReflexKeys.ATTACKED
import game.idle.flow.ReflexKeys.BELOW
import game.idle.flow.ReflexKeys.BELOW_LABEL
import game.idle.flow.ReflexKeys.BELOW_RANGE
import game.idle.flow.ReflexKeys.BELOW_WORDS
import game.idle.flow.ReflexKeys.DEFAULT_BELOW
import game.idle.flow.ReflexKeys.DO
import game.idle.flow.ReflexKeys.DRINK
import game.idle.flow.ReflexKeys.EAT
import game.idle.flow.ReflexKeys.FOODS
import game.idle.flow.ReflexKeys.HITPOINTS
import game.idle.flow.ReflexKeys.JUMP
import game.idle.flow.ReflexKeys.POTIONS
import game.idle.flow.ReflexKeys.SKILL
import game.idle.flow.ReflexKeys.SKILL_BELOW
import game.idle.flow.ReflexKeys.SKILL_NAME
import game.idle.flow.ReflexKeys.SKILL_RANGE
import game.idle.flow.ReflexKeys.SKILL_WORDS
import game.idle.flow.ReflexKeys.STEP
import game.idle.flow.ReflexKeys.STOP
import game.idle.flow.ReflexKeys.WHEN
import io.luna.game.model.mob.Skill

/**
 * Checks a flow's reflexes against its steps (S07c): a jump becomes the index of the step whose id it holds, so it
 * follows that step wherever it moved, and a step that was deleted makes the reflex unable to work. [portions] maps
 * every item id Luna can eat to the ids of its food's every portion, so a reflex picking a cake eats its slices too.
 * [doses] maps each listed potion to its dose ids, lowest dose first.
 */
class ReflexResolver(private val portions: Map<Int, Set<Int>>, private val doses: Map<Int, List<Int>>) {

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
        val type = reflex[WHEN] ?: HITPOINTS
        return when (type) {
            HITPOINTS -> ReflexTrigger.HitpointsBelow(below(reflex[BELOW]))
            SKILL -> ReflexTrigger.SkillBelow(skill(reflex[SKILL_NAME]), skillBelow(reflex[SKILL_BELOW]))
            ATTACKED -> ReflexTrigger.UnderAttack
            else -> throw FlowError("'$type' is not a choice for When")
        }
    }

    private fun skill(name: String?): Int {
        if (name == null) throw FlowError(Reasons.needs("Skill"))
        val id = Skill.NAMES.indexOfFirst { it.equals(name, ignoreCase = true) }
        if (id < 0) throw FlowError(Reasons.notA("Skill", name))
        if (id == Skill.HITPOINTS) throw FlowError("Use When Hitpoints for hitpoints")
        return id
    }

    private fun skillBelow(text: String?): Int {
        if (text == null) throw FlowError("Needs a Below level")
        return text.toIntOrNull()?.takeIf { it in SKILL_RANGE } ?: throw FlowError(Reasons.takes(BELOW_LABEL, SKILL_WORDS, text))
    }

    private fun below(text: String?): Int {
        if (text == null) return DEFAULT_BELOW
        return text.toIntOrNull()?.takeIf { it in BELOW_RANGE } ?: throw FlowError(Reasons.takes(BELOW_LABEL, BELOW_WORDS, text))
    }

    private fun action(reflex: ReflexSettings, steps: List<StepSettings>): ReflexAction {
        val what = reflex[DO] ?: EAT
        return when (what) {
            EAT -> ReflexAction.Eat(foods(reflex))
            DRINK -> ReflexAction.Drink(potions(reflex))
            STOP -> ReflexAction.StopFlow
            JUMP -> ReflexAction.JumpTo(jumpTarget(reflex[STEP], steps))
            else -> throw FlowError("'$what' is not something a reflex does")
        }
    }

    private fun foods(reflex: ReflexSettings): Set<Int> =
        StepItems.parse(reflex[FOODS]).flatMap { portions[it.id] ?: throw FlowError(Reasons.notA("Food", it.id.toString(), countable = false)) }.toSet()

    private fun potions(reflex: ReflexSettings): List<Int> =
        StepItems.parse(reflex[POTIONS]).ifEmpty { throw FlowError(Reasons.needs("Potion")) }
            .flatMap { doses[it.id] ?: throw FlowError(Reasons.notA("Potion", it.id.toString())) }

    private fun jumpTarget(step: String?, steps: List<StepSettings>): Int {
        val id = step?.toIntOrNull() ?: throw FlowError(Reasons.needs("Step"))
        return steps.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: throw FlowError("Jumps to a step that was deleted")
    }
}
