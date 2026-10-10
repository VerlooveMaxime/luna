package game.idle.autopilot.smithing

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ProcessInput
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepInput
import game.idle.flow.StepNeeds
import game.idle.flow.StepRadius
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.ToolNeed
import game.idle.flow.Uses
import game.idle.flow.WorkSpot
import game.idle.flow.option.GameNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.StepTarget
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.smithing.BarType
import game.skill.smithing.Smithing
import game.skill.smithing.smithBar.SmithingItem
import game.skill.smithing.smithBar.SmithingTable
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/**
 * Smith: smiths a count of an item or, without one, as many as the bars carried make, at an anvil within a radius of
 * the work spot. The item is kept by its id, its metal and table row following from it (S06b, Maxime 2026-10-09). The
 * bars come from earlier steps or the bank (S07a), named with [names].
 */
class SmithStepType(private val names: GameNames) : StepType {

    override val kind = "smith"

    override val label = "smith"

    override val description = "Smiths bars into items at an anvil, from earlier steps or the bank."

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Skill(Skill.SMITHING)

    override fun skill(settings: StepSettings): Int = Skill.SMITHING

    override fun target(names: GameNames): StepTarget = StepTarget(ITEM, SmithItemOptions(names))

    override fun input(settings: StepSettings, before: FlowContext): InputSource = ProcessInput.read(settings, before, BAR_IDS)

    override fun newSettings(before: FlowContext): StepSettings = ProcessInput.initial(StepSettings(kind), before, BAR_IDS)

    override fun details(settings: StepSettings, context: FlowContext): List<String> =
        listOf(
            StepInput.detail("Bars", input(settings, context), context, smithed(settings)?.let { setOf(it.second.barType.id) }.orEmpty()),
            StepAmount.detail(settings, unbounded = "all of them"),
        )

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            ProcessInput.field(BAR_IDS),
            StepField.Search("Item", target(names), "What would you like to smith?"),
            StepField.Note("Uses") { settings, _ -> uses(settings) },
            StepAmount.field("Amount", unbounded = "all of them", button = "All"),
            StepRadius.field(),
        )

    override fun summary(settings: StepSettings): String {
        val name = smithed(settings)?.let { (_, smithed) -> names.item(smithed.item.id).lowercase() } ?: settings[ITEM] ?: "?"
        return "smith ${StepAmount.prefix(settings)}$name${StepRadius.suffix(settings)}"
    }

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val item = settings[ITEM] ?: throw FlowError("smith needs an item")
        val (table, smithed) = smithed(settings) ?: throw FlowError("'$item' is not an item to smith")
        ProcessInput.requireGathered(input(settings, context), context, listOf(smithed.barType.id), names)
        return SmithStep(smithed.barType, table, StepRadius.read(settings), context.workSpot, StepAmount.read(settings))
    }

    private fun uses(settings: StepSettings): String =
        smithed(settings)?.let { (table, smithed) -> Uses.text(mapOf(smithed.barType.id to table.bars), names) } ?: Uses.NOTHING_PICKED

    /** The table row and the item of it [settings] keep, null when no row smiths it. */
    private fun smithed(settings: StepSettings): Pair<SmithingTable, SmithingItem>? {
        val id = settings[ITEM]?.toIntOrNull() ?: return null
        return SmithingTable.entries.firstNotNullOfOrNull { table -> table.items.firstOrNull { it.item.id == id }?.let { table to it } }
    }

    companion object {
        const val ITEM = "item"

        private val BAR_IDS: Set<Int> = BarType.entries.map { it.id }.toSet()
    }
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

    override fun needs(): List<StepNeeds> =
        listOf(StepNeeds(tools = listOf(ToolNeed(word = null, mapOf(Smithing.HAMMER to 1))), inputs = listOf(metal.id)))

    override fun activity(player: Player, runTile: Tile): StepActivity =
        SmithingActivity(LunaSmither(player, metal, table, Area(workSpot.tile(runTile), radius)), made.level, amount)
}
