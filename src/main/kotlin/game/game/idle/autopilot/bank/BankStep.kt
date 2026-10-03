package game.idle.autopilot.bank

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepField
import game.idle.flow.StepType
import game.idle.location.Location
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/** `bank deposit all`: deposits everything but the player's axes at the bank of the location the flow is at. */
object BankStepType : StepType {

    override val keyword = "bank"

    override val label = "bank deposit all"

    override val usage = "bank deposit all"

    override val fields = emptyList<StepField>()

    override fun parse(words: List<String>): List<String> {
        if (words != listOf("deposit", "all")) throw FlowError("The only bank step is 'bank deposit all'")
        return emptyList()
    }

    override fun line(values: List<String>): String = "bank deposit all"

    override fun resolve(values: List<String>, context: FlowContext): ResolvedStep {
        val at = context.location ?: throw FlowError("bank comes after a chop step, so the flow knows which bank to use")
        val booth = at.bank ?: throw FlowError("${at.name} has no bank")
        return BankStep(at, booth)
    }
}

/** A bank step resolved: the booth on [booth] at [location]. */
data class BankStep(val location: Location, val booth: Tile) : ResolvedStep {

    override fun activity(player: Player): StepActivity = BankActivity(LunaBanker(player, booth.toPosition()))
}
