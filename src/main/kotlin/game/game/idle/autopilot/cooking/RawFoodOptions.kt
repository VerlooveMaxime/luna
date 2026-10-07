package game.idle.autopilot.cooking

import game.idle.flow.option.GameNames
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionSource
import game.idle.flow.option.ProcessOptions
import game.idle.flow.option.StepOption
import game.skill.cooking.cookFood.Food
import io.luna.game.model.mob.Skill

/** The raw food a cook step can cook, by the raw item's id. */
class RawFoodOptions(private val names: GameNames) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> =
        Food.entries.mapNotNull { food ->
            ProcessOptions.option(context, food.raw, names.item(food.raw), listOf(food.raw), Skill.COOKING, food.lvl)
        }
}
