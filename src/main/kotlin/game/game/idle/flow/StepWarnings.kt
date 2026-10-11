package game.idle.flow

import game.idle.flow.option.GameNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.OptionFacts

/**
 * The configure screen's warnings that never stop a flow (S07b): a tool or bait a step needs that the player neither
 * carries nor has a bank step withdraw, and an input a step takes from the bank that no bank step withdraws. Bank steps
 * anywhere in the flow count, since it loops (Maxime, 2026-10-10). Then a kind's own ([OwnWarnings], S07d), a step that
 * fills the bag with items no bank or drop step of the flow takes out of it (Maxime, 2026-10-11), and a step that fights
 * with no reflex attached that eats or runs (S07c). Items are named with [names].
 */
class StepWarnings(private val resolver: FlowResolver, private val names: GameNames) {

    /**
     * The warnings for the step at [index] of [steps], none when it does not resolve (its reason shows instead); [facts]
     * say what the player carries and wears, [reflexes] are the flow's. A step that can work several ways is warned about
     * the first way, unless one of them needs no warning.
     */
    fun of(steps: List<StepSettings>, index: Int, facts: OptionFacts, reflexes: List<ReflexSettings>): List<String> {
        val resolved = resolver.resolvedEach(steps)
        val step = resolved.getOrNull(index) ?: return emptyList()
        val banking = banking(resolved)
        val before = resolver.contextBefore(steps, index)
        val fromBank = resolver.types.input(steps[index], before) == InputSource.BANK
        val ways = step.needs().map { way -> warnings(way, banking, facts, fromBank) }
        val needs = if (ways.any { it.isEmpty() }) emptyList() else ways.firstOrNull().orEmpty()
        val own = resolver.types.warnings(steps[index], before, facts)
        val emptying = NO_EMPTYING.takeIf { step is FillsBag && step.fills.none { id -> emptied(resolved, id) } }
        return needs + own + listOfNotNull(emptying, NO_SURVIVAL.takeIf { step is Fights && !survives(steps[index], reflexes) })
    }

    private fun emptied(resolved: List<ResolvedStep?>, id: Int): Boolean = resolved.any { it is EmptiesBag && it.empties(id) }

    /**
     * The warning for [need] when the flow of [steps] does not supply it, null when it does: an eat reflex's food (S07c),
     * judged as a step's tool is.
     */
    fun supplies(steps: List<StepSettings>, need: ToolNeed, facts: OptionFacts): String? = tool(need, banking(resolver.resolvedEach(steps)), facts)

    private fun banking(resolved: List<ResolvedStep?>): List<Banking> =
        resolved.withIndex().mapNotNull { (at, other) -> (other as? BankMoves)?.let { Banking(at + 1, it) } }

    /** Whether a reflex attached to [step] eats or runs: every reflex S07c builds does one or the other. */
    private fun survives(step: StepSettings, reflexes: List<ReflexSettings>): Boolean = reflexes.any { it.id in step.reflexes }

    private fun warnings(needs: StepNeeds, banking: List<Banking>, facts: OptionFacts, fromBank: Boolean): List<String> {
        val inputs = needs.inputs.filter { fromBank && banking.none { bank -> it in bank.moves.withdraws } }
        return needs.tools.mapNotNull { tool(it, banking, facts) } + inputs.map { "No bank step withdraws ${names.item(it).lowercase()}." }
    }

    /**
     * Worn (for a tool Luna counts worn, [ToolNeed.worn]) or withdrawn is enough; carried is enough unless every carried
     * one is banked by a bank step, which the warning names (Maxime, 2026-10-10).
     */
    private fun tool(need: ToolNeed, banking: List<Banking>, facts: OptionFacts): String? {
        val usable = need.allowed(facts::level)
        if (usable.any { need.worn && it in facts.worn || banking.any { bank -> it in bank.moves.withdraws } }) return null
        val word = need.word ?: names.item(need.items.keys.first()).lowercase()
        val withdrawn = "no bank step withdraws ${if (need.countable) "one" else "any"}."
        val carried = usable.filter { it in facts.bag }
        if (carried.isEmpty()) return "You carry no $word and $withdrawn"
        if (carried.any { id -> banking.none { it.moves.banks(id) } }) return null
        val first = banking.first { it.moves.banks(carried.first()) }
        return "Step ${first.number} banks your $word and $withdrawn"
    }

    /** A bank step of the flow and its [number] there (from 1). */
    private data class Banking(val number: Int, val moves: BankMoves)

    companion object {
        const val NO_SURVIVAL = "No reflex eats or runs during this step."
        const val NO_EMPTYING = "No step empties the bag."
    }
}
