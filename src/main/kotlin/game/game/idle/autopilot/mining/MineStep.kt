package game.idle.autopilot.mining

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
import game.skill.mining.Ore
import io.luna.game.model.mob.Player

/**
 * `mine [<n>] <ore> [within <r>]`: one kind of ore from rocks within r tiles of the work spot, n ores or, without n,
 * until the inventory is full.
 */
object MineStepType : StepType {

    /** The amount field's word for "until the inventory is full". */
    const val FULL = "full"

    /** The ores Luna has rocks for, easiest first. Essence is left out: its rock is a mine of its own. */
    val MINEABLE: List<Ore> by lazy {
        Ore.entries.filter { it.rocks.isNotEmpty() && it != Ore.RUNE_ESSENCE }.sortedBy { it.level }
    }

    override val keyword = "mine"

    override val label = "mine"

    override val usage = "mine [<n>] <ore> [within <r>]"

    override val fields = listOf(
        StepField.Choice("ore") { MINEABLE.map { it.name.lowercase() } },
        StepField.Choice("amount") { listOf(FULL) + StepAmount.COUNTS },
        StepRadius.field(),
    )

    override fun parse(words: List<String>): List<String> {
        val (count, afterCount) = StepAmount.split(words)
        val ore = afterCount.firstOrNull() ?: throw FlowError("mine needs an ore: $usage")
        val radius = StepRadius.parse(afterCount.drop(1), after = "ore", usage)
        return listOf(ore, StepAmount.value(count, FULL), radius.toString())
    }

    override fun line(values: List<String>): String =
        "mine ${StepAmount.prefix(values[AMOUNT])}${values[ORE]}${StepRadius.suffix(values[RADIUS])}"

    override fun resolve(values: List<String>, context: FlowContext): ResolvedStep {
        val name = values[ORE]
        val ore = MINEABLE.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw FlowError("'$name' is not an ore with rocks to mine")
        return MineStep(ore, StepRadius.check(values[RADIUS]), context.workSpot, StepAmount.count(values[AMOUNT]))
    }

    private const val ORE = 0
    private const val AMOUNT = 1
    private const val RADIUS = 2
}

/**
 * A mine step resolved: which ore, how far from the work spot, and how many ([amount], null for a full inventory).
 * Later steps know it gathers that ore.
 */
data class MineStep(val ore: Ore, val radius: Int, val workSpot: WorkSpot, val amount: Int? = null) : ResolvedStep {

    override fun after(context: FlowContext): FlowContext = context.copy(gathered = context.gathered + ore.item)

    override fun activity(player: Player, runTile: Tile): StepActivity =
        MiningActivity(LunaMiner(player, ore, Area(workSpot.tile(runTile), radius)), ore, amount)
}
