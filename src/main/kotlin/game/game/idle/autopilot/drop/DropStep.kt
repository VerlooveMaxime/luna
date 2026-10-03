package game.idle.autopilot.drop

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepField
import game.idle.flow.StepType
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/** `drop`: drops what the steps before it gathered. */
object DropStepType : StepType {

    override val keyword = "drop"

    override val label = "drop"

    override val usage = "drop"

    override val fields = emptyList<StepField>()

    override fun parse(words: List<String>): List<String> {
        if (words.isNotEmpty()) throw FlowError("drop takes nothing after it: it drops what the chop steps before it gathered")
        return emptyList()
    }

    override fun line(values: List<String>): String = "drop"

    override fun resolve(values: List<String>, context: FlowContext): ResolvedStep {
        if (context.gathered.isEmpty()) throw FlowError("drop comes after a chop step, so the flow knows what to drop")
        return DropStep(context.gathered)
    }
}

/** A drop step resolved: [itemIds] is what the steps before it gather. */
data class DropStep(val itemIds: Set<Int>) : ResolvedStep {

    override fun activity(player: Player, runTile: Tile): StepActivity = DropActivity(LunaItemDropper(player, itemIds))
}
