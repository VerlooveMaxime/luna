package game.idle.flow.option

import game.idle.flow.FlowContext
import game.idle.flow.StepSettings
import game.idle.location.Tile
import io.luna.game.model.mob.Skill

/** The options of one setting that picks from a list, unordered ([OptionOrder] orders them). */
fun interface OptionSource {
    fun options(context: OptionContext): List<StepOption>
}

/**
 * The setting under [key] a step mainly picks, which the builder's slot shows by its option's label and picture, and
 * the options [source] offers for it; [default] is what a step without the setting picks.
 */
data class StepTarget(val key: String, val source: OptionSource, val default: String? = null) {

    /** The option [settings] picked, null when nothing is picked or the value is no option. */
    fun picked(settings: StepSettings): StepOption? {
        val value = settings[key] ?: default ?: return null
        return source.options(OptionContext(input = InputSource.BANK)).firstOrNull { it.value == value }
    }
}

/** Where a processing step takes what it works on: what the steps before it get, or the bank (Maxime, S01). */
enum class InputSource { EARLIER_STEPS, BANK }

/**
 * What a step's options are worked out from: the player's [facts], the step's own [settings], what the steps [before] it
 * set up, where its [input] comes from, and [here], the player's tile, which stands for the work spot when no walk step
 * comes before (the flow then works where Run is pressed).
 */
data class OptionContext(
    val facts: OptionFacts = OptionFacts(),
    val settings: StepSettings = StepSettings(),
    val before: FlowContext = FlowContext(),
    val input: InputSource = InputSource.EARLIER_STEPS,
    val here: Tile? = null,
)

/**
 * The player's levels options grey on, by skill id, what the bank holds, by item id, and what the player carries: the
 * ids in the [bag] and those [worn], which the configure screen's tool warnings read (S07b).
 */
data class OptionFacts(
    val levels: Map<Int, Int> = emptyMap(),
    val bank: Map<Int, Int> = emptyMap(),
    val bag: Set<Int> = emptySet(),
    val worn: Set<Int> = emptySet(),
) {

    fun level(skill: Int): Int = levels[skill] ?: 1

    fun banked(item: Int): Int = bank[item] ?: 0

    /** Why an option needing [level] in [skill] cannot be picked yet, null when it can. */
    fun lacks(skill: Int, level: Int): String? =
        if (level(skill) >= level) null else "needs ${Skill.getName(skill)} $level"

    /** What the bank holds of [item], as a row's note says it. */
    fun bankNote(item: Int): String = banked(item).let { if (it == 0) "none in bank" else "$it in bank" }
}

/** The names the cache gives items, npcs and objects, as the client shows them. */
interface GameNames {
    fun item(id: Int): String

    fun npc(id: Int): String

    fun obj(id: Int): String
}
