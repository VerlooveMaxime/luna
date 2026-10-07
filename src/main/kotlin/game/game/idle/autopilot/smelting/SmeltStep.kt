package game.idle.autopilot.smelting

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
import game.skill.smithing.BarType
import io.luna.game.model.mob.Player

/**
 * Smelt: smelts a count of bars or, without one, every bar the ores carried make, at a furnace within a radius of the
 * work spot.
 */
object SmeltStepType : StepType {

    const val BAR = "bar"

    override val kind = "smelt"

    override val label = "smelt"

    override val fields = listOf(
        StepField.Choice(BAR, "bar") { BarType.entries.sortedBy { it.level }.map { it.name.lowercase() } },
        StepAmount.field(unbounded = "all"),
        StepRadius.field(),
    )

    override fun summary(settings: StepSettings): String =
        "smelt ${StepAmount.prefix(settings)}${settings[BAR] ?: "?"}${StepRadius.suffix(settings)}"

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val name = settings[BAR] ?: throw FlowError("smelt needs a bar")
        val bar = BarType.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw FlowError("'$name' is not a bar to smelt")
        return SmeltStep(bar, StepRadius.read(settings), context.workSpot, StepAmount.read(settings))
    }
}

/** A smelt step resolved: the bar, where, and how many ([amount], null for all). Later steps know it makes that bar. */
data class SmeltStep(val bar: BarType, val radius: Int, val workSpot: WorkSpot, val amount: Int? = null) : ResolvedStep {

    override fun after(context: FlowContext): FlowContext = context.copy(gathered = context.gathered + bar.id)

    override fun activity(player: Player, runTile: Tile): StepActivity =
        SmeltingActivity(LunaSmelter(player, bar, Area(workSpot.tile(runTile), radius)), bar, amount)
}
