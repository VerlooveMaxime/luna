package game.idle.autopilot.pickup

import game.idle.flow.FlowContext
import game.idle.flow.StepRadius
import game.idle.flow.StepSettings
import game.idle.flow.WorkSpot
import game.idle.flow.option.GameNames
import game.idle.flow.option.ItemCatalog
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionFacts
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepOption
import game.idle.location.Area

/**
 * What a pick-up step can pick up (Maxime, S01): the drops of the npcs the fight step before it fights, then the items
 * that spawn within its distance of the work spot, then any other item. Every item can be picked; a note is listed as
 * the item it stands for, which the step takes in both forms (S07d).
 */
class PickUpOptions(
    private val drops: DropLookup,
    private val spawns: SpawnCatalog,
    private val items: ItemCatalog,
    private val names: GameNames,
) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> {
        val fought = context.before.fought
        val dropped = dropsBefore(context.before).map(items::unnoted).toSet()
        val dropNote = fought.minOrNull()?.let { "${names.npc(it)} drop" }.orEmpty()
        val spawned = spawnsAround(context.settings, context.before, context.facts).map(items::unnoted).toSet() - dropped
        val near = dropped.map { row(it, names.item(it), dropNote, DROPS) } +
            spawned.map { row(it, names.item(it), "spawns here", SPAWNS) }
        val nearNames = near.map { it.label }.toSet()
        val others = items.items(context.facts).filterValues { it !in nearNames }
        return near + others.map { (id, name) -> row(id, name, "any item", group = 0) }
    }

    /**
     * The items that spawn within the step's distance of its work spot: the walk step's tile before it, else where the
     * player stands (Run will be pressed there); none when that is not known.
     */
    fun spawnsAround(settings: StepSettings, before: FlowContext, facts: OptionFacts): Set<Int> {
        val spot = when (val spot = before.workSpot) {
            WorkSpot.RunTile -> facts.here
            is WorkSpot.At -> spot.tile
        }
        return spot?.let { spawns.itemsIn(Area(it, StepRadius.read(settings))) }.orEmpty()
    }

    /** The items the npcs of the fight step before drop, as the step's own drops and the shared tables they roll. */
    fun dropsBefore(before: FlowContext): Set<Int> = before.fought.flatMap(drops::itemsOf).toSet()

    private fun row(id: Int, name: String, note: String, group: Int) =
        StepOption(value = id.toString(), label = name, icon = OptionIcon.Item(id), note = note, group = group)

    private companion object {
        const val DROPS = -2
        const val SPAWNS = -1
    }
}
