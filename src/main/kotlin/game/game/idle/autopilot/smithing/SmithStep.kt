package game.idle.autopilot.smithing

import game.idle.flow.Counts
import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.LevelNeed
import game.idle.flow.OwnSpot
import game.idle.flow.ProcessInput
import game.idle.flow.Reasons
import game.idle.flow.ResolvedStep
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepInput
import game.idle.flow.StepNeeds
import game.idle.flow.StepRadius
import game.idle.flow.StepSettings
import game.idle.flow.StepSpot
import game.idle.flow.StepType
import game.idle.flow.StepWork
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
            StepInput.detail(input(settings, context), context, smithed(settings)?.let { setOf(it.second.barType.id) }.orEmpty()),
            StepAmount.detail(settings, unbounded = "all of them"),
        )

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            ProcessInput.field(BAR_IDS),
            StepField.Search("Item", target(names), "What would you like to smith?"),
            StepField.Note("Uses") { settings, _ -> uses(settings) },
            StepAmount.field(StepAmount.LABEL, unbounded = "all of them", button = "All"),
            StepSpot.field(),
            StepRadius.field(),
        )

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val item = settings[ITEM] ?: throw FlowError(Reasons.needs("Item"))
        val (table, smithed) = smithed(settings) ?: throw FlowError(Reasons.notA("Item to smith", item))
        ProcessInput.requireGathered(input(settings, context), context, listOf(smithed.barType.id), names)
        return SmithStep(smithed.barType, table, StepRadius.read(settings), StepSpot.of(settings, context), StepAmount.read(settings))
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

        /** The hammer, which Luna's smithing takes from the bag only. */
        val HAMMER = ToolNeed(word = null, mapOf(Smithing.HAMMER to 1))

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
    override val amount: Int? = null,
) : ResolvedStep, OwnSpot, Counts {

    override val ownArea: Area? get() = workSpot.ownArea(radius)

    private val made = table.items.first { it.barType == metal }

    override fun after(context: FlowContext): FlowContext = workSpot.after(context.copy(gathered = context.gathered + made.item.id))

    /** A hammer, the bar and the item's Smithing level. */
    override fun needs(): List<StepNeeds> =
        listOf(StepNeeds(tools = listOf(SmithStepType.HAMMER), inputs = listOf(metal.id), level = LevelNeed(Skill.SMITHING, made.level)))

    override fun work(player: Player, runTile: Tile): StepWork =
        SmithingWork(LunaSmither(player, metal, table, Area(workSpot.tile(runTile), radius)))
}
