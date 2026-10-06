package game.idle.autopilot.smithing

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepRadius
import game.idle.flow.StepType
import game.idle.flow.WorkSpot
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.smithing.BarType
import game.skill.smithing.smithBar.SmithingTable
import io.luna.game.model.mob.Player

/**
 * `smith [<n>] <metal> <item> [within <r>]`: smiths n of an item or, without n, as many as the bars carried make, at an
 * anvil within r tiles of the work spot.
 */
object SmithStepType : StepType {

    /** The amount field's word for "as many as the bars make". */
    const val ALL = "all"

    /** The bars Luna smiths items from, easiest first. */
    val METALS: List<BarType> by lazy {
        BarType.entries.filter { SmithingTable.BAR_TO_ITEM.containsKey(it) }.sortedBy { it.level }
    }

    override val keyword = "smith"

    override val label = "smith"

    override val usage = "smith [<n>] <metal> <item> [within <r>]"

    override val fields = listOf(
        StepField.Choice("metal") { METALS.map { it.name.lowercase() } },
        StepField.Choice("item") { values -> itemsOf(metal(values[METAL]) ?: METALS.first()).map { it.name.lowercase() } },
        StepField.Choice("amount") { listOf(ALL) + StepAmount.COUNTS },
        StepRadius.field(),
    )

    /** The items [metal] makes, easiest first. */
    fun itemsOf(metal: BarType): List<SmithingTable> =
        SmithingTable.entries.filter { item(it, metal) != null }.sortedBy { item(it, metal)?.level }

    override fun parse(words: List<String>): List<String> {
        val (count, afterCount) = StepAmount.split(words)
        if (afterCount.size < 2) throw FlowError("smith needs a metal and an item: $usage")
        val radius = StepRadius.parse(afterCount.drop(2), after = "item", usage)
        return listOf(afterCount[0], afterCount[1], StepAmount.value(count, ALL), radius.toString())
    }

    override fun line(values: List<String>): String =
        "smith ${StepAmount.prefix(values[AMOUNT])}${values[METAL]} ${values[ITEM]}${StepRadius.suffix(values[RADIUS])}"

    override fun resolve(values: List<String>, context: FlowContext): ResolvedStep {
        val metal = metal(values[METAL]) ?: throw FlowError("'${values[METAL]}' is not a metal to smith")
        val table = itemsOf(metal).firstOrNull { it.name.equals(values[ITEM], ignoreCase = true) }
            ?: throw FlowError("There is no ${values[METAL]} ${values[ITEM]} to smith")
        return SmithStep(metal, table, StepRadius.check(values[RADIUS]), context.workSpot, StepAmount.count(values[AMOUNT]))
    }

    private fun metal(name: String): BarType? = METALS.firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun item(table: SmithingTable, metal: BarType) = table.items.firstOrNull { it.barType == metal }

    private const val METAL = 0
    private const val ITEM = 1
    private const val AMOUNT = 2
    private const val RADIUS = 3
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
