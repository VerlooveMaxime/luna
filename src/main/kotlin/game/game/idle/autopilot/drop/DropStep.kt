package game.idle.autopilot.drop

import game.idle.flow.Choice
import game.idle.flow.EmptiesBag
import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.Reasons
import game.idle.flow.ResolvedStep
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepItems
import game.idle.flow.StepPick
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.StepWork
import game.idle.flow.option.CarriedItemOptions
import game.idle.flow.option.GameNames
import game.idle.flow.option.ItemCatalog
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/** What a drop step drops (Maxime, S01; words as the bank's Deposit since S07f): what the flow gathers, or chosen items. */
enum class DropChoice(val value: String, val word: String) {
    GATHERED("gathered", "Gathered"),
    CHOSEN("chosen", "Chosen"),
}

/**
 * Drop: drops, where it stands, what any step of the flow gathers or makes (Maxime, 2026-10-11: as the bank's Gathered),
 * or the items picked, any of [items], named with [names].
 */
class DropStepType(private val items: ItemCatalog, private val names: GameNames) : StepType {

    override val kind = "drop"

    override val label = "drop"

    override val description = "Drops what the flow gathers or the items picked, where it stands."

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Media("sideicons", 3)

    /** The items picked on Chosen, with the first one's picture; nothing on Gathered. */
    override fun pick(settings: StepSettings, names: GameNames): StepPick? =
        if (choice(settings) == DropChoice.CHOSEN) StepPick.items(picked(settings), names) else null

    override fun details(settings: StepSettings, context: FlowContext): List<String> =
        if (choice(settings) == DropChoice.GATHERED) listOf("gathered items") else emptyList()

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            StepField.Toggle(DROP, "Drop", DropChoice.entries.map { Choice(it.value, it.word) }, current = { settings, _ -> choice(settings).value }),
            StepField.Note("Drops", visible = { choice(it) == DropChoice.GATHERED }) { _, before ->
                StepPick.items(before.lap.toList(), names).label ?: "Nothing any step gathers yet"
            },
            StepField.Items(
                ITEMS, "Chosen", CarriedItemOptions(items, names), "What would you like to drop?", "+ Add or remove items...", rows = ROWS,
                visible = { choice(it) == DropChoice.CHOSEN },
            ),
        )

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep =
        when (choice(settings)) {
            DropChoice.GATHERED -> DropStep(context.lap.ifEmpty { throw FlowError(Reasons.needs("step that gathers something")) })
            DropChoice.CHOSEN -> DropStep(chosen(settings).flatMap(items::alike).toSet())
        }

    private fun chosen(settings: StepSettings): List<Int> =
        picked(settings).ifEmpty { throw FlowError(Reasons.needs("Chosen items", countable = false)) }

    /** The items picked that name an item; text naming none is left out. */
    private fun picked(settings: StepSettings): List<Int> = StepItems.ids(settings, ITEMS).filter { items.alike(it).isNotEmpty() }

    companion object {
        const val DROP = "drop"
        const val ITEMS = "items"

        /** The toggle takes the column's first row; the list the other five. */
        private const val ROWS = 5

        /** The choice kept, Gathered by default (S01). */
        fun choice(settings: StepSettings): DropChoice = DropChoice.entries.firstOrNull { it.value == settings[DROP] } ?: DropChoice.GATHERED
    }
}

/** A drop step resolved: [itemIds] is what it drops, the flow's gathered items or every item of the names picked. */
data class DropStep(val itemIds: Set<Int>) : ResolvedStep, EmptiesBag {

    override fun empties(id: Int): Boolean = id in itemIds

    override fun work(player: Player, runTile: Tile): StepWork = DropWork(LunaItemDropper(player, itemIds))
}
