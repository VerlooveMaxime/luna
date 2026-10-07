package game.idle.autopilot.mining

import game.idle.flow.option.GameNames
import game.idle.flow.option.GatherOptions
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepOption
import io.luna.game.model.mob.Skill

/** The ores a mine step can mine, named after the ore (every rock in the cache is called "Rocks"). */
class RockOptions(private val names: GameNames) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> =
        MineStepType.MINEABLE.map { ore ->
            GatherOptions.option(
                context,
                value = ore.name.lowercase(),
                label = names.item(ore.item),
                item = ore.item,
                skill = Skill.MINING,
                level = ore.level,
            )
        }
}
