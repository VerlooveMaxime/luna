package game.idle.autopilot.fighting

import game.idle.flow.option.GameNames
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepOption
import game.player.item.consume.food.Food

/** Every npc a fight step can fight, with its combat level; nothing greys them (no requirement in Luna's data). */
class FightOptions(private val catalog: FightTargetCatalog) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> =
        catalog.targets.map { target ->
            StepOption(
                value = target.name,
                label = target.label,
                icon = OptionIcon.Npc(target.npcs.min()),
                note = levelNote(target.levels),
                level = target.levels.first,
            )
        }

    private fun levelNote(levels: IntRange): String =
        if (levels.first == levels.last) "level ${levels.first}" else "level ${levels.first}-${levels.last}"
}

/** What a fight step can eat: any food it carries (kept on top), or one of Luna's foods, with the bank's counts. */
class EatOptions(private val names: GameNames) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> =
        listOf(StepOption(ANY, "Any food", OptionIcon.Item(Food.BREAD.id), note = "the first in the bag", group = -1)) +
            Food.entries.map { food ->
                StepOption(
                    value = food.id.toString(),
                    label = names.item(food.id),
                    icon = OptionIcon.Item(food.id),
                    note = context.facts.bankNote(food.id),
                    banked = context.facts.banked(food.id),
                )
            }

    companion object {
        const val ANY = "any"
    }
}
