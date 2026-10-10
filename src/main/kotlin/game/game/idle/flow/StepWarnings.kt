package game.idle.flow

import game.idle.flow.option.GameNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.OptionFacts

/**
 * The configure screen's warnings that never stop a flow (S07b): a tool or bait a step needs that the player neither
 * carries nor has a bank step withdraw, and an input a step takes from the bank that no bank step withdraws. Bank steps
 * anywhere in the flow count, since it loops (Maxime, 2026-10-10). Items are named with [names].
 */
class StepWarnings(private val resolver: FlowResolver, private val names: GameNames) {

    /**
     * The warnings for the step at [index] of [steps], none when it does not resolve (its reason shows instead); [facts]
     * say what the player carries and wears. A step that can work several ways is warned about the first way, unless
     * one of them needs no warning.
     */
    fun of(steps: List<StepSettings>, index: Int, facts: OptionFacts): List<String> {
        val resolved = resolver.resolvedEach(steps)
        val step = resolved.getOrNull(index) ?: return emptyList()
        val banking = resolved.withIndex().mapNotNull { (at, other) -> (other as? BankMoves)?.let { Banking(at + 1, it) } }
        val fromBank = resolver.types.input(steps[index], resolver.contextBefore(steps, index)) == InputSource.BANK
        val ways = step.needs().map { way -> warnings(way, banking, facts, fromBank) }
        return if (ways.any { it.isEmpty() }) emptyList() else ways.firstOrNull().orEmpty()
    }

    private fun warnings(needs: StepNeeds, banking: List<Banking>, facts: OptionFacts, fromBank: Boolean): List<String> {
        val inputs = needs.inputs.filter { fromBank && banking.none { bank -> it in bank.moves.withdraws } }
        return needs.tools.mapNotNull { tool(it, banking, facts) } + inputs.map { "No bank step withdraws ${names.item(it).lowercase()}." }
    }

    /**
     * Worn or withdrawn is enough; carried is enough unless every carried one is banked by a bank step, which the
     * warning names (Maxime, 2026-10-10).
     */
    private fun tool(need: ToolNeed, banking: List<Banking>, facts: OptionFacts): String? {
        val usable = need.items.filter { (_, level) -> need.skill == null || facts.level(need.skill) >= level }.keys
        if (usable.any { it in facts.worn || banking.any { bank -> it in bank.moves.withdraws } }) return null
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
}
