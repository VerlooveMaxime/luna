package game.idle.autopilot.drop

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.option.GameNames
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/** Drop: drops what the steps before it gathered. */
object DropStepType : StepType {

    override val kind = "drop"

    override val label = "drop"

    override val description = "Drops what the steps before it gathered."

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Media("sideicons", 3)

    override fun details(settings: StepSettings, context: FlowContext): List<String> = listOf("what was gathered")

    override fun fields(names: GameNames): List<StepField> = listOf(StepField.Note("Drop") { _, _ -> "What was gathered" })

    override fun summary(settings: StepSettings): String = "drop"

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        if (context.gathered.isEmpty()) throw FlowError("drop needs a gathering step before it")
        return DropStep(context.gathered)
    }
}

/** A drop step resolved: [itemIds] is what the steps before it gather. */
data class DropStep(val itemIds: Set<Int>) : ResolvedStep {

    override fun activity(player: Player, runTile: Tile): StepActivity = DropActivity(LunaItemDropper(player, itemIds))
}
