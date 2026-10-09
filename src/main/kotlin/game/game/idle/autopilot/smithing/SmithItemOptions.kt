package game.idle.autopilot.smithing

import game.idle.flow.option.GameNames
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionSource
import game.idle.flow.option.ProcessOptions
import game.idle.flow.option.StepOption
import game.skill.smithing.smithBar.SmithingTable
import io.luna.game.model.mob.Skill

/**
 * Everything a smith step can smith, of every metal, by the item's id, which its item setting keeps (since S06b). From
 * earlier steps only the items of the bars they get are offered.
 */
class SmithItemOptions(private val names: GameNames) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> =
        SmithingTable.entries.flatMap { it.items }.mapNotNull { smithed ->
            val id = smithed.item.id
            val bars = listOf(smithed.barType.id)
            ProcessOptions.option(context, id, names.item(id), bars, Skill.SMITHING, smithed.level)
        }
}
