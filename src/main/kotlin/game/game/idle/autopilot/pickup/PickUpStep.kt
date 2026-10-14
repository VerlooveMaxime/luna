package game.idle.autopilot.pickup

import game.idle.flow.Counts
import game.idle.flow.FillsBag
import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.OwnSpot
import game.idle.flow.OwnWarnings
import game.idle.flow.Reasons
import game.idle.flow.ResolvedStep
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepItems
import game.idle.flow.StepPick
import game.idle.flow.StepRadius
import game.idle.flow.StepSettings
import game.idle.flow.StepSpot
import game.idle.flow.StepType
import game.idle.flow.StepWork
import game.idle.flow.WorkSpot
import game.idle.flow.option.GameNames
import game.idle.flow.option.ItemCatalog
import game.idle.flow.option.OptionFacts
import game.idle.location.Area
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/**
 * Pick up (S07d): takes the items picked lying around the work spot, nearest first, then ends; with a count, after that
 * many pickups. It never waits for more to fall (Maxime, 2026-10-10). The items are offered by [PickUpOptions] over
 * [drops], [spawns] and [items], named with [names].
 */
class PickUpStepType(drops: DropLookup, spawns: SpawnCatalog, private val items: ItemCatalog, private val names: GameNames) : StepType, OwnWarnings {

    private val options = PickUpOptions(drops, spawns, items, names)

    override val kind = "pickup"

    override val label = "pick up"

    override val description = "Picks up the items picked lying around the work spot, nearest first."

    override val goal = true

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Item(LEATHER_GLOVES)

    override fun pick(settings: StepSettings, names: GameNames): StepPick = StepPick.items(picked(settings), names)

    override fun details(settings: StepSettings, context: FlowContext): List<String> = listOf(StepAmount.detail(settings, unbounded = ALL_LYING))

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            StepAmount.field(StepAmount.LABEL, unbounded = ALL_LYING, button = "All"),
            StepField.Items(ITEMS, "Items", options, "What would you like to pick up?", add = "+ Add or remove items...", rows = ROWS),
            StepSpot.field(),
            StepRadius.field(),
        )

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val picked = picked(settings)
        if (picked.isEmpty()) throw FlowError(Reasons.needs("Items", countable = false))
        return PickUpStep(picked.flatMap(items::alike).toSet(), StepRadius.read(settings), StepSpot.of(settings, context), StepAmount.read(settings))
    }

    /**
     * The picked items that neither the fight step before drops nor spawn around the work spot (Maxime, 2026-10-11): only
     * other players could leave them, so the step warns, though it may run.
     */
    override fun warnings(settings: StepSettings, before: FlowContext, facts: OptionFacts): List<String> {
        val around = options.dropsBefore(before) + options.spawnsAround(settings, before, facts)
        val missing = picked(settings).filter { item -> items.alike(item).none { it in around } }
        if (missing.isEmpty()) return emptyList()
        return listOf("No fight step before drops ${missing.joinToString(", ") { names.item(it).lowercase() }}, and none spawns here")
    }

    /** The items picked that name an item; text naming none is left out. */
    private fun picked(settings: StepSettings): List<Int> = StepItems.ids(settings, ITEMS).filter { items.alike(it).isNotEmpty() }

    companion object {
        const val ITEMS = "items"

        const val ALL_LYING = "all lying around"

        const val LEATHER_GLOVES = 1059

        /** Amount takes the column's first row; the list the other five (S07 plan). */
        private const val ROWS = 5
    }
}

/**
 * A pick-up step resolved: [itemIds] are every item it takes (the picked ones, the others of their names and their
 * notes), within [radius] of its work spot, [amount] pickups or, without one, all lying there. Later steps know what it
 * picks up as gathered (S07 plan: the bank's Gathered banks the loot).
 */
data class PickUpStep(
    val itemIds: Set<Int>,
    val radius: Int,
    val workSpot: WorkSpot,
    override val amount: Int? = null,
) : ResolvedStep, OwnSpot, Counts, FillsBag {

    override val ownArea: Area? get() = workSpot.ownArea(radius)

    override val fills: Set<Int> get() = itemIds

    override fun after(context: FlowContext): FlowContext = workSpot.after(context.copy(gathered = context.gathered + fills))

    override fun work(player: Player, runTile: Tile): StepWork =
        PickUpWork(LunaPicker(player, itemIds, Area(workSpot.tile(runTile), radius)))
}
