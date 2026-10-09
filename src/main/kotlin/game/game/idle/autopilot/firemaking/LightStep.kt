package game.idle.autopilot.firemaking

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepInput
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.option.GameNames
import game.idle.location.Tile
import game.skill.firemaking.Log
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/** Light: lights a count of the logs the steps before it gathered or, without one, all of them. */
object LightStepType : StepType {

    override val kind = "light"

    override val label = "light"

    override val description = "Lights the logs earlier steps get."

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Skill(Skill.FIREMAKING)

    override fun skill(settings: StepSettings): Int = Skill.FIREMAKING

    override fun details(settings: StepSettings, context: FlowContext): List<String> =
        listOf(StepInput.detail("Logs", context, LOG_IDS), StepAmount.detail(settings, unbounded = "all of them"))

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            StepField.Note("Input") { _, context -> StepInput.detail("Logs", context, LOG_IDS) },
            StepAmount.field("Amount", unbounded = "all of them", button = "All"),
        )

    override fun summary(settings: StepSettings): String = "light ${StepAmount.prefix(settings)}".trimEnd()

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val logs = context.gathered intersect LOG_IDS
        if (logs.isEmpty()) throw FlowError("light needs logs from a step before it")
        return LightStep(logs, StepAmount.read(settings))
    }

    private val LOG_IDS: Set<Int> = Log.entries.map { it.id }.toSet()
}

/** A light step resolved: the logs it lights and how many ([amount], null for all). */
data class LightStep(val logIds: Set<Int>, val amount: Int? = null) : ResolvedStep {

    override fun activity(player: Player, runTile: Tile): StepActivity = LightActivity(LunaLighter(player, logIds), amount)
}
