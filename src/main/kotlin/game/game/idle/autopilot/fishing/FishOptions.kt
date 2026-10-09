package game.idle.autopilot.fishing

import game.idle.flow.option.GameNames
import game.idle.flow.option.GatherOptions
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepOption
import io.luna.game.model.mob.Skill

/**
 * The fishing methods a fish step can use, one row each named by the fish it catches and its tool ("Trout, salmon (fly
 * fishing rod)"), greyed below the tool's level; the note adds the levels of the fish that come later ("Fishing 20
 * (salmon 30)"). Only raw fish are named: a big net's caskets and seaweed are not what the row is picked for.
 */
class FishOptions(private val names: GameNames) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> = FishingMethod.ALL.map { option(context, it) }

    private fun option(context: OptionContext, method: FishingMethod): StepOption {
        val fish = method.tool.fish.filter { names.item(it.id).startsWith(RAW) }
        val caught = fish.joinToString(", ") { fishName(it.id) }.let { it.take(1).uppercase() + it.drop(1) }
        val row = GatherOptions.option(
            context,
            value = method.word,
            label = "$caught (${names.item(method.tool.id).lowercase()})",
            item = fish.first().id,
            skill = Skill.FISHING,
            level = method.tool.level,
        )
        val later = fish.filter { it.level > method.tool.level }.joinToString(", ") { "${fishName(it.id)} ${it.level}" }
        return if (later.isEmpty()) row else row.copy(note = "${row.note} ($later)")
    }

    private fun fishName(id: Int): String = names.item(id).removePrefix(RAW).lowercase()

    private companion object {
        const val RAW = "Raw "
    }
}
