package game.idle.autopilot.reflex

import game.idle.flow.option.GameNames
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepOption
import game.player.item.consume.food.Food

/**
 * The foods an eat reflex picks from (S07c): Luna's eat table, one row a food by its first portion's id, its note what
 * it heals and how much of it the bank holds, every portion counted (a cake and its slices).
 */
class FoodOptions(private val names: GameNames) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> =
        Food.entries.map { food ->
            val banked = food.ids.sumOf(context.facts::banked)
            StepOption(
                value = food.id.toString(),
                label = names.item(food.id),
                icon = OptionIcon.Item(food.id),
                note = "heals ${food.heal}, ${if (banked == 0) "none" else banked} in bank",
                banked = banked,
            )
        }

    companion object {
        /** Every portion's id of the food each id Luna can eat belongs to, as `ReflexResolver` and `ReflexForm` take it. */
        fun portions(): Map<Int, Set<Int>> = Food.ID_TO_FOOD.mapValues { it.value.ids }
    }
}
