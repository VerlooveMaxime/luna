package game.idle.autopilot.smelting

import game.idle.flow.option.GameNames
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionSource
import game.idle.flow.option.ProcessOptions
import game.idle.flow.option.StepOption
import game.skill.smithing.BarType
import io.luna.game.model.mob.Skill

/**
 * The bars a smelt step can smelt, kept under the word its setting uses (`bronze`). From earlier steps a bar is offered
 * only when they get every ore it needs.
 */
class BarOptions(private val names: GameNames) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> =
        BarType.entries.mapNotNull { bar ->
            val ores = listOfNotNull(bar.oreRequired.first, bar.oreRequired.second).map { it.id }
            ProcessOptions.option(
                context, bar.id, names.item(bar.id), ores, Skill.SMITHING, bar.level, value = bar.name.lowercase(),
            )
        }
}
