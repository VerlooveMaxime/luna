package game.idle.autopilot.mining

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepRadius
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.WorkSpot
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.mining.Ore
import io.luna.game.model.mob.Player

/** Mine: one kind of ore from rocks within a radius of the work spot, a count of ores or, without one, until full. */
object MineStepType : StepType {

    const val ORE = "ore"

    /** The ores Luna has rocks for, easiest first. Essence is left out: its rock is a mine of its own. */
    val MINEABLE: List<Ore> by lazy {
        Ore.entries.filter { it.rocks.isNotEmpty() && it != Ore.RUNE_ESSENCE }.sortedBy { it.level }
    }

    override val kind = "mine"

    override val label = "mine"

    override val fields = listOf(
        StepField.Choice(ORE, "ore") { MINEABLE.map { it.name.lowercase() } },
        StepAmount.field(unbounded = "full"),
        StepRadius.field(),
    )

    override fun summary(settings: StepSettings): String =
        "mine ${StepAmount.prefix(settings)}${settings[ORE] ?: "?"}${StepRadius.suffix(settings)}"

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val name = settings[ORE] ?: throw FlowError("mine needs an ore")
        val ore = MINEABLE.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw FlowError("'$name' is not an ore with rocks to mine")
        return MineStep(ore, StepRadius.read(settings), context.workSpot, StepAmount.read(settings))
    }
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
