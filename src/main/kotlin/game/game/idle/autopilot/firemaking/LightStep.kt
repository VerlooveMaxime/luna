package game.idle.autopilot.firemaking

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.location.Tile
import game.skill.firemaking.Log
import io.luna.game.model.mob.Player

/** Light: lights a count of the logs the steps before it gathered or, without one, all of them. */
object LightStepType : StepType {

    override val kind = "light"

    override val label = "light"

    override val fields = listOf(StepAmount.field(unbounded = "all"))

    override fun summary(settings: StepSettings): String = "light ${StepAmount.prefix(settings)}".trimEnd()

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val logs = context.gathered intersect LOG_IDS
        if (logs.isEmpty()) throw FlowError("light comes after a chop step, so the flow knows which logs to light")
        return LightStep(logs, StepAmount.read(settings))
    }

    private val LOG_IDS: Set<Int> = Log.entries.map { it.id }.toSet()
}

/** A light step resolved: the logs it lights and how many ([amount], null for all). */
data class LightStep(val logIds: Set<Int>, val amount: Int? = null) : ResolvedStep {

    override fun activity(player: Player, runTile: Tile): StepActivity = LightActivity(LunaLighter(player, logIds), amount)
}
