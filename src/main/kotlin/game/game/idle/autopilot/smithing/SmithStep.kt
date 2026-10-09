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
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.smithing.BarType
import game.skill.smithing.smithBar.SmithingTable
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/**
 * Smith: smiths a count of an item or, without one, as many as the bars carried make, at an anvil within a radius of
 * the work spot.
 */
object SmithStepType : StepType {

    const val METAL = "metal"

    const val ITEM = "item"

    /** The bars Luna smiths items from, easiest first. */
    val METALS: List<BarType> by lazy {
        BarType.entries.filter { SmithingTable.BAR_TO_ITEM.containsKey(it) }.sortedBy { it.level }
    }

    override val kind = "smith"

    override val label = "smith"

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Skill(Skill.SMITHING)

    /** Metal and item are two settings until S07 makes them one item, so they show as text. */
    override fun details(settings: StepSettings, context: FlowContext): List<String> =
        listOf(
            listOfNotNull(settings[METAL], settings[ITEM]).joinToString(" ").ifEmpty { "not set yet" },
            StepAmount.detail(settings, unbounded = "all of them"),
        )

    override val fields = listOf(
        StepField.Choice(METAL, "metal") { METALS.map { it.name.lowercase() } },
        StepField.Choice(ITEM, "item") { settings ->
            itemsOf(settings[METAL]?.let(::metal) ?: METALS.first()).map { it.name.lowercase() }
        },
        StepAmount.field(unbounded = "all"),
        StepRadius.field(),
    )

    /** The items [metal] makes, easiest first. */
    fun itemsOf(metal: BarType): List<SmithingTable> =
        SmithingTable.entries.filter { item(it, metal) != null }.sortedBy { item(it, metal)?.level }

    override fun summary(settings: StepSettings): String =
        "smith ${StepAmount.prefix(settings)}${settings[METAL] ?: "?"} ${settings[ITEM] ?: "?"}${StepRadius.suffix(settings)}"

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val metalName = settings[METAL] ?: throw FlowError("smith needs a metal")
        val itemName = settings[ITEM] ?: throw FlowError("smith needs an item")
        val metal = metal(metalName) ?: throw FlowError("'$metalName' is not a metal to smith")
        val table = itemsOf(metal).firstOrNull { it.name.equals(itemName, ignoreCase = true) }
            ?: throw FlowError("There is no $metalName $itemName to smith")
        return SmithStep(metal, table, StepRadius.read(settings), context.workSpot, StepAmount.read(settings))
    }

    private fun metal(name: String): BarType? = METALS.firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun item(table: SmithingTable, metal: BarType) = table.items.firstOrNull { it.barType == metal }
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
