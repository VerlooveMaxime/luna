package game.idle.autopilot.reflex

import game.idle.flow.option.GameNames
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepOption
import game.player.item.consume.potion.Potion

/** Luna's potions, one row per kind, with its full vial's picture and the number of doses in the bank. */
class PotionOptions(private val names: GameNames) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> =
        Potion.entries.map { potion ->
            val banked = potion.doses.withIndex().sumOf { (index, id) -> (index + 1) * context.facts.banked(id) }
            StepOption(
                value = potion.fourDose.toString(),
                label = names.item(potion.fourDose).removeSuffix("(4)").trimEnd(),
                icon = OptionIcon.Item(potion.fourDose),
                note = "$banked doses in bank",
                banked = banked,
            )
        }

    companion object {
        /** Each listed potion's dose ids, lowest dose first. */
        fun doses(): Map<Int, List<Int>> = Potion.entries.associate { it.fourDose to it.doses }
    }
}
