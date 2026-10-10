package game.idle.autopilot.firemaking

import game.idle.flow.FlowContext
import game.idle.flow.ProcessInput
import game.idle.flow.ProcessKinds
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepInput
import game.idle.flow.StepPick
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.option.GameNames
import game.idle.flow.option.InputSource
import game.idle.location.Tile
import game.skill.firemaking.Log
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/**
 * Light: lights a count of the kinds of logs picked or, without one, all of them, where the player stands. The logs come
 * from earlier steps or the bank (S07a), named with [names].
 */
class LightStepType(private val names: GameNames) : StepType {

    private val logs = ProcessKinds(LOGS, kind = "light", what = "logs", usable = Log.entries.map { it.id }.toSet(), names)

    override val kind = "light"

    override val label = "light"

    override val description = "Lights the logs picked where it stands, from earlier steps or the bank."

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Skill(Skill.FIREMAKING)

    override fun skill(settings: StepSettings): Int = Skill.FIREMAKING

    override fun pick(settings: StepSettings, names: GameNames): StepPick = logs.pick(settings)

    override fun input(settings: StepSettings, before: FlowContext): InputSource = ProcessInput.read(settings, before, logs.usable)

    override fun newSettings(before: FlowContext): StepSettings {
        val settings = ProcessInput.initial(StepSettings(kind), before, logs.usable)
        return logs.initial(settings, before, input(settings, before))
    }

    override fun details(settings: StepSettings, context: FlowContext): List<String> =
        listOf(
            StepInput.detail("Logs", input(settings, context), context, logs.ids(settings).toSet()),
            StepAmount.detail(settings, unbounded = "all of them"),
        )

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            ProcessInput.field(logs.usable),
            StepAmount.field("Amount", unbounded = "all of them", button = "All"),
            logs.field("Logs", LogOptions(names), "Which logs would you like to light?"),
        )

    override fun summary(settings: StepSettings): String =
        "light ${StepAmount.prefix(settings)}${logs.ids(settings).joinToString(", ") { names.item(it).lowercase() }}".trimEnd()

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep =
        LightStep(logs.resolve(settings, context, input(settings, context)), StepAmount.read(settings))

    companion object {
        const val LOGS = "logs"
    }
}

/** A light step resolved: the logs it lights and how many ([amount], null for all). */
data class LightStep(val logIds: Set<Int>, val amount: Int? = null) : ResolvedStep {

    override fun activity(player: Player, runTile: Tile): StepActivity = LightActivity(LunaLighter(player, logIds), amount)
}
