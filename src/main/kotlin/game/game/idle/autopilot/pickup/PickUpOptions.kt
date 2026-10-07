package game.idle.autopilot.pickup

import game.idle.flow.StepRadius
import game.idle.flow.WorkSpot
import game.idle.flow.option.GameNames
import game.idle.flow.option.ItemCatalog
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepOption
import game.idle.location.Area
import game.idle.location.Tile

/**
 * What a pick-up step can pick up (Maxime, S01): the drops of the npcs the fight step before it fights, then the items
 * that spawn within its distance of the work spot, then any other item. Every item can be picked.
 */
class PickUpOptions(
    private val drops: DropLookup,
    private val spawns: SpawnCatalog,
    private val items: ItemCatalog,
    private val names: GameNames,
) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> {
        val fought = context.before.fought
        val dropped = fought.flatMap(drops::itemsOf).toSet()
        val dropNote = fought.minOrNull()?.let { "${names.npc(it)} drop" }.orEmpty()
        val spawned = spawnsAround(context) - dropped
        val near = dropped.map { row(it, names.item(it), dropNote, DROPS) } +
            spawned.map { row(it, names.item(it), "spawns here", SPAWNS) }
        val nearNames = near.map { it.label }.toSet()
        val others = items.items(context.facts).filterValues { it !in nearNames }
        return near + others.map { (id, name) -> row(id, name, "any item", group = 0) }
    }

    private fun spawnsAround(context: OptionContext): Set<Int> =
        workSpot(context)?.let { spawns.itemsIn(Area(it, StepRadius.read(context.settings))) }.orEmpty()

    /** Where the step works: the walk step's tile before it, else where the player stands (Run will be pressed there). */
    private fun workSpot(context: OptionContext): Tile? =
        when (val spot = context.before.workSpot) {
            WorkSpot.RunTile -> context.here
            is WorkSpot.At -> spot.tile
        }

    private fun row(id: Int, name: String, note: String, group: Int) =
        StepOption(value = id.toString(), label = name, icon = OptionIcon.Item(id), note = note, group = group)

    private companion object {
        const val DROPS = -2
        const val SPAWNS = -1
    }
}
