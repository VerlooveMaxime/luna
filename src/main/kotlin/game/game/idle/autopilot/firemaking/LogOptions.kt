package game.idle.autopilot.firemaking

import game.idle.flow.option.GameNames
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionSource
import game.idle.flow.option.ProcessOptions
import game.idle.flow.option.StepOption
import game.skill.firemaking.Log
import io.luna.game.model.mob.Skill

/** The logs a light step can light, by item id. */
class LogOptions(private val names: GameNames) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> =
        Log.entries.mapNotNull { log ->
            ProcessOptions.option(context, log.id, names.item(log.id), listOf(log.id), Skill.FIREMAKING, log.level)
        }
}
