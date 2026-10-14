package game.idle.flow

import game.idle.flow.option.GameNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.ItemCatalog
import game.idle.flow.option.OptionFacts

/**
 * Every kind of step a flow can use, in the order the builder's kind picker shows them, found by [StepType.kind]. The
 * Until rows every counted kind shares (S12) are added here, a bank goal's item being one of [items].
 */
class StepTypes(val all: List<StepType>, items: ItemCatalog = ItemCatalog(emptyMap())) {

    init {
        require(all.isNotEmpty()) { "A flow needs at least one kind of step" }
        require(all.map { it.kind }.toSet().size == all.size) { "Two kinds of step share a name" }
    }

    private val byKind = all.associateBy { it.kind }

    private val goals = GoalForm(items)

    fun find(kind: String): StepType? = byKind[kind]

    /** [type]'s configure rows, the Until rows first on the right for a kind that takes a goal, options named with [names]. */
    fun fields(type: StepType, names: GameNames): List<StepField> = (if (type.goal) goals.fields(type) else emptyList()) + type.fields(names)

    /** The goal [step] holds, null for none or for a kind that takes none; throws [FlowError] naming what is wrong. */
    fun goal(step: StepSettings): ResolvedGoal? = find(step.kind)?.takeIf { it.goal }?.let { goals.read(step, it) }

    /** Where [step] takes what it works on, after what the steps [before] it set up; the bank for a kind no type knows. */
    fun input(step: StepSettings, before: FlowContext): InputSource = find(step.kind)?.input(step, before) ?: InputSource.BANK

    /** [step]'s own warnings after what the steps [before] it set up, the player as [facts] say; none for a kind without. */
    fun warnings(step: StepSettings, before: FlowContext, facts: OptionFacts): List<String> =
        (find(step.kind) as? OwnWarnings)?.warnings(step, before, facts).orEmpty()
}
