package game.idle.autopilot.firemaking

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepType
import game.idle.location.Tile
import game.skill.firemaking.Log
import io.luna.game.model.mob.Player

/** `light [<n>]`: lights n of the logs the steps before it gathered or, without n, all of them. */
object LightStepType : StepType {

    /** The amount field's word for "every log". */
    const val ALL = "all"

    override val keyword = "light"

    override val label = "light"

    override val usage = "light [<n>]"

    override val fields = listOf(StepField.Choice("amount") { listOf(ALL) + StepAmount.COUNTS })

    override fun parse(words: List<String>): List<String> {
        val (count, rest) = StepAmount.split(words)
        if (rest.isNotEmpty()) throw FlowError("light takes only a count: $usage. It lights the logs the steps before it gathered")
        return listOf(StepAmount.value(count, ALL))
    }

    override fun line(values: List<String>): String = "light ${StepAmount.prefix(values[0])}".trimEnd()

    override fun resolve(values: List<String>, context: FlowContext): ResolvedStep {
        val logs = context.gathered intersect LOG_IDS
        if (logs.isEmpty()) throw FlowError("light comes after a chop step, so the flow knows which logs to light")
        return LightStep(logs, StepAmount.count(values[0]))
    }

    private val LOG_IDS: Set<Int> = Log.entries.map { it.id }.toSet()
}

/** A light step resolved: the logs it lights and how many ([amount], null for all). */
data class LightStep(val logIds: Set<Int>, val amount: Int? = null) : ResolvedStep {

    override fun activity(player: Player, runTile: Tile): StepActivity = LightActivity(LunaLighter(player, logIds), amount)
}
