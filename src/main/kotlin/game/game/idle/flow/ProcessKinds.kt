package game.idle.flow

import game.idle.flow.option.GameNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.OptionSource

/**
 * The kinds of item a light or cook step works on (Maxime, 2026-10-10: several, since a fly-fishing step catches trout
 * and salmon), kept under [key] as a list of item ids among [usable], named with [names]. [kind] is the step's kind and
 * [what] the items' word ("logs", "raw food").
 */
class ProcessKinds(
    private val key: String,
    private val kind: String,
    private val what: String,
    val usable: Set<Int>,
    private val names: GameNames,
) {

    fun ids(settings: StepSettings): List<Int> = StepItems.ids(settings, key).filter { it in usable }

    /** A new step's: on earlier steps every kind they get, in flow order (Maxime, 2026-10-10); on the bank none. */
    fun initial(settings: StepSettings, before: FlowContext, input: InputSource): StepSettings {
        if (input == InputSource.BANK) return settings
        val gathered = before.gatheredBy.keys.filter { it in usable }
        return settings.with(key, StepItems.text(gathered.map(::StepItem)))
    }

    /** "Oak logs, willow logs" with the first kind's picture; no label when none is picked. */
    fun pick(settings: StepSettings): StepPick {
        val ids = ids(settings)
        val label = ids.mapIndexed { index, id -> names.item(id).let { if (index == 0) it else it.lowercase() } }.joinToString(", ")
        return StepPick(label.ifEmpty { null }, ids.firstOrNull()?.let(OptionIcon::Item))
    }

    /** The list on the configure screen, over [source], under [label]. */
    fun field(label: String, source: OptionSource, title: String): StepField.Items =
        StepField.Items(key, label, source, title, add = "+ Add or remove $what...", rows = ROWS)

    /** The kinds picked, checked: some must be, and on earlier steps a step before must get each; throws [FlowError]. */
    fun resolve(settings: StepSettings, before: FlowContext, input: InputSource): Set<Int> {
        val ids = ids(settings)
        if (ids.isEmpty()) throw FlowError("$kind needs $what picked")
        ProcessInput.requireGathered(input, before, ids, names)
        return ids.toSet()
    }

    private companion object {
        /** Input and Amount take the column's first two rows; the list the rest (S07 plan). */
        const val ROWS = 4
    }
}
