package game.idle.autopilot.smithing

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepRadius
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.WorkSpot
import game.idle.flow.option.GameNames
import game.idle.flow.option.StepTarget
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.smithing.BarType
import game.skill.smithing.smithBar.SmithingItem
import game.skill.smithing.smithBar.SmithingTable
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/**
 * Smith: smiths a count of an item or, without one, as many as the bars carried make, at an anvil within a radius of
 * the work spot. The item is kept by its id, its metal and table row following from it (S06b, Maxime 2026-10-09).
 */
object SmithStepType : StepType {

    const val ITEM = "item"

    override val kind = "smith"

    override val label = "smith"

    override val description = "Smiths bars into items at an anvil."

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Skill(Skill.SMITHING)

    override fun skill(settings: StepSettings): Int = Skill.SMITHING

    override fun target(names: GameNames): StepTarget = StepTarget(ITEM, SmithItemOptions(names))

    override fun details(settings: StepSettings, context: FlowContext): List<String> =
        listOf(StepAmount.detail(settings, unbounded = "all of them"))

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            StepField.Search("Item", target(names), "What would you like to smith?"),
            StepAmount.field("Amount", unbounded = "all of them", button = "All"),
            StepRadius.field(),
        )

    override fun summary(settings: StepSettings): String {
        val item = settings[ITEM]
        val name = item?.toIntOrNull()?.let(::smithed)?.let { (_, smithed) -> smithed.item.itemDef.name.lowercase() } ?: item ?: "?"
        return "smith ${StepAmount.prefix(settings)}$name${StepRadius.suffix(settings)}"
    }

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val item = settings[ITEM] ?: throw FlowError("smith needs an item")
        val (table, smithed) = item.toIntOrNull()?.let(::smithed) ?: throw FlowError("'$item' is not an item to smith")
        return SmithStep(smithed.barType, table, StepRadius.read(settings), context.workSpot, StepAmount.read(settings))
    }

    /** The table row and the item of it whose id is [id], null when no row smiths it. */
    private fun smithed(id: Int): Pair<SmithingTable, SmithingItem>? =
        SmithingTable.entries.firstNotNullOfOrNull { table -> table.items.firstOrNull { it.item.id == id }?.let { table to it } }
}

/**
 * A smith step resolved: the [metal] and the [table] row it makes, where, and how many ([amount], null for as many as
 * the bars make). Later steps know it makes that item.
 */
data class SmithStep(
    val metal: BarType,
    val table: SmithingTable,
    val radius: Int,
    val workSpot: WorkSpot,
    val amount: Int? = null,
) : ResolvedStep {

    private val made = table.items.first { it.barType == metal }

    override fun after(context: FlowContext): FlowContext = context.copy(gathered = context.gathered + made.item.id)

    override fun activity(player: Player, runTile: Tile): StepActivity =
        SmithingActivity(LunaSmither(player, metal, table, Area(workSpot.tile(runTile), radius)), made.level, amount)
}
