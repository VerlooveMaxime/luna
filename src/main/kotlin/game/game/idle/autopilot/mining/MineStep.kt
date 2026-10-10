package game.idle.autopilot.mining

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepNeeds
import game.idle.flow.StepRadius
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.ToolNeed
import game.idle.flow.WorkSpot
import game.idle.flow.option.GameNames
import game.idle.flow.option.StepTarget
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.mining.Ore
import game.skill.mining.Pickaxe
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/** Mine: one kind of ore from rocks within a radius of the work spot, a count of ores or, without one, until full. */
object MineStepType : StepType {

    const val ORE = "ore"

    /** The ores Luna has rocks for, easiest first. Essence is left out: its rock is a mine of its own. */
    val MINEABLE: List<Ore> by lazy {
        Ore.entries.filter { it.rocks.isNotEmpty() && it != Ore.RUNE_ESSENCE }.sortedBy { it.level }
    }

    override val kind = "mine"

    override val label = "mine"

    override val description = "Mines one kind of rock around the work spot."

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Skill(Skill.MINING)

    override fun skill(settings: StepSettings): Int = Skill.MINING

    override fun target(names: GameNames): StepTarget = StepTarget(ORE, RockOptions(names))

    override fun details(settings: StepSettings, context: FlowContext): List<String> =
        listOf(StepAmount.detail(settings, unbounded = "until the bag is full"))

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            StepField.Search("Rock", target(names), "Which rock would you like to mine?"),
            StepAmount.field("Amount", unbounded = "until the bag is full", button = "Full"),
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

    /** Any pickaxe the player's Mining level allows, as Luna's mining takes one carried or wielded. */
    override fun needs(): List<StepNeeds> =
        listOf(StepNeeds(tools = listOf(ToolNeed("pickaxe", Pickaxe.entries.associate { it.id to it.level }, Skill.MINING))))

    override fun activity(player: Player, runTile: Tile): StepActivity =
        MiningActivity(LunaMiner(player, ore, Area(workSpot.tile(runTile), radius)), ore, amount)
}
