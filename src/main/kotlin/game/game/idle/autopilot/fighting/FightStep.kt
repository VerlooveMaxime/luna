package game.idle.autopilot.fighting

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
import io.luna.game.model.mob.Player

/** When the fight step eats: a share of the player's full hitpoints, so a flow keeps working after a reset brings them back to 10. */
object EatBelow {

    const val KEY = "eatBelow"

    const val DEFAULT = 50

    val CHOICES = listOf(25, 50, 75).map { it.toString() }

    fun field(): StepField.Choice =
        StepField.Choice(KEY, "eat below", default = DEFAULT.toString(), display = { "$it%" }) { CHOICES }

    /** The share [settings] hold, [DEFAULT] without one; throws [FlowError] when it is not 1 to 99. */
    fun read(settings: StepSettings): Int {
        val text = settings[KEY] ?: return DEFAULT
        val percent = text.toIntOrNull()
        if (percent == null || percent !in 1..99) {
            throw FlowError("eat below takes a share of your hitpoints from 1 to 99 percent, not '$text'")
        }
        return percent
    }

    /** The end of a summary: nothing for the default. */
    fun suffix(settings: StepSettings): String =
        settings[KEY]?.takeIf { it != DEFAULT.toString() }?.let { " eat below $it%" } ?: ""
}

/**
 * Fight: fights the npcs of a [FightTargetCatalog] target around the work spot one at a time, a count of kills or,
 * without one, until stopped. Food in the inventory is eaten once hitpoints fall below a share of full; with none left
 * the player runs from what attacks them and the flow stops.
 */
class FightStepType(private val catalog: FightTargetCatalog) : StepType {

    /** The v1 builder cycles through every target, weakest first, until the search replaces it (S06). */
    private val names: List<String> =
        catalog.targets.sortedWith(compareBy({ it.levels.first }, { it.name })).map { it.name }

    override val kind = "fight"

    override val label = "fight"

    override val fields = listOf(
        StepField.Choice(NPC, "npc") { names },
        StepAmount.field(unbounded = "nonstop"),
        StepRadius.field(),
        EatBelow.field(),
    )

    override fun summary(settings: StepSettings): String =
        "fight ${StepAmount.prefix(settings)}${settings[NPC] ?: "?"}${StepRadius.suffix(settings)}${EatBelow.suffix(settings)}"

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val name = settings[NPC]?.lowercase() ?: throw FlowError("fight needs an npc")
        val target = catalog.find(name)
            ?: throw FlowError("'$name' is not something you can fight")
        return FightStep(target, StepRadius.read(settings), context.workSpot, StepAmount.read(settings), EatBelow.read(settings))
    }

    companion object {
        const val NPC = "npc"
    }
}

/** A fight step resolved: [amount] kills (null for no end), eating below [eatBelow] % of full hitpoints. */
data class FightStep(
    val target: FightTarget,
    val radius: Int,
    val workSpot: WorkSpot,
    val amount: Int? = null,
    val eatBelow: Int = EatBelow.DEFAULT,
) : ResolvedStep {

    override fun after(context: FlowContext): FlowContext = context.copy(fought = target.npcs)

    override fun activity(player: Player, runTile: Tile): StepActivity =
        FightingActivity(LunaFighter(player, target, Area(workSpot.tile(runTile), radius)), eatBelow, amount)
}
