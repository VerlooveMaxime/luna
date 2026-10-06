package game.idle.autopilot.smelting

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
import io.luna.game.model.mob.Player

/**
 * `smelt [<n>] <bar> [within <r>]`: smelts n bars or, without n, every bar the ores carried make, at a furnace within r
 * tiles of the work spot.
 */
object SmeltStepType : StepType {

    /** The amount field's word for "every bar the ores make". */
    const val ALL = "all"

    override val keyword = "smelt"

    override val label = "smelt"

    override val usage = "smelt [<n>] <bar> [within <r>]"

    override val fields = listOf(
        StepField.Choice("bar") { BarType.entries.sortedBy { it.level }.map { it.name.lowercase() } },
        StepField.Choice("amount") { listOf(ALL) + StepAmount.COUNTS },
        StepRadius.field(),
    )

    override fun parse(words: List<String>): List<String> {
        val (count, afterCount) = StepAmount.split(words)
        val bar = afterCount.firstOrNull() ?: throw FlowError("smelt needs a bar: $usage")
        val radius = StepRadius.parse(afterCount.drop(1), after = "bar", usage)
        return listOf(bar, StepAmount.value(count, ALL), radius.toString())
    }

    override fun line(values: List<String>): String =
        "smelt ${StepAmount.prefix(values[AMOUNT])}${values[BAR]}${StepRadius.suffix(values[RADIUS])}"

    override fun resolve(values: List<String>, context: FlowContext): ResolvedStep {
        val name = values[BAR]
        val bar = BarType.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw FlowError("'$name' is not a bar to smelt")
        return SmeltStep(bar, StepRadius.check(values[RADIUS]), context.workSpot, StepAmount.count(values[AMOUNT]))
    }

    private const val BAR = 0
    private const val AMOUNT = 1
    private const val RADIUS = 2
}

/** A smelt step resolved: the bar, where, and how many ([amount], null for all). Later steps know it makes that bar. */
data class SmeltStep(val bar: BarType, val radius: Int, val workSpot: WorkSpot, val amount: Int? = null) : ResolvedStep {

    override fun after(context: FlowContext): FlowContext = context.copy(gathered = context.gathered + bar.id)

    override fun activity(player: Player, runTile: Tile): StepActivity =
        SmeltingActivity(LunaSmelter(player, bar, Area(workSpot.tile(runTile), radius)), bar, amount)
}
