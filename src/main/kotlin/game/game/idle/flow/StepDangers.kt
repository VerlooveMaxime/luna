package game.idle.flow

import game.idle.danger.DangerCatalog
import game.idle.danger.Threat
import game.idle.flow.option.GameNames
import game.idle.flow.option.OptionFacts
import game.idle.location.Area

/**
 * The aggressive npcs within reach of where a step works, which its slot and configure screen warn about (S08; warn
 * only, Maxime, 2026-10-07). Every kind warns (Maxime, 2026-10-14): one with a Within row works that far around its own
 * spot or where the flow went before it; one without works on that tile alone; a walk step on the tile it goes to.
 * Where the flow went is the player's tile when nothing is known yet (Run will be pressed there), as the pick-up
 * options read it.
 */
class StepDangers(private val resolver: FlowResolver, private val dangers: DangerCatalog, private val names: GameNames) {

    /** The threats to the player [facts] describe at the step at [index] of [steps]; none for a step that cannot work. */
    fun of(steps: List<StepSettings>, index: Int, facts: OptionFacts): List<Threat> {
        val area = area(steps, index, facts) ?: return emptyList()
        return dangers.threats(area).filter { it.threatens(facts.combat) }
    }

    /** Where the step at [index] works, null for a step that cannot work or whose spot is not known. */
    fun area(steps: List<StepSettings>, index: Int, facts: OptionFacts): Area? {
        val settings = steps[index]
        val type = resolver.types.find(settings.kind) ?: return null
        if (resolver.resolvedEach(steps)[index] == null) return null
        val spot = resolver.contextBefore(steps, index + 1).workSpot.known() ?: facts.here ?: return null
        val within = type.fields(names).filterIsInstance<StepField.Typed>().any { it.key == StepRadius.KEY }
        return Area(spot, if (within) StepRadius.read(settings) else 0)
    }
}
