package game.idle.flow

import game.idle.flow.option.GameNames
import game.idle.flow.option.StepTarget
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/** A step of a flow the player could not have meant; the message is shown as is. */
class FlowError(override val message: String) : RuntimeException(message)

/**
 * One step of a flow as the player set it up: its kind and its settings by name, as text. This is what a save
 * keeps, so it stays Gson-friendly (every field has a default) and a setting a step has no value for reads as its
 * default: settings added later need no conversion of older saves. An empty value is never kept.
 */
data class StepSettings(val kind: String = "", val values: Map<String, String> = emptyMap()) {

    operator fun get(key: String): String? = values[key]

    /** These settings with [key] set to [value], or without it when [value] is empty. */
    fun with(key: String, value: String): StepSettings =
        copy(values = if (value.isEmpty()) values - key else values + (key to value))
}

/**
 * One kind of flow step, everything about it in one place: which settings the flow builder shows for it, how it reads
 * in a few words, and what it resolves to. The runner, resolver and builder only know this interface, so a new kind of
 * step is a new implementation added to the registry (`IdleSteps`).
 */
interface StepType {

    /** The name a saved step gives its kind. */
    val kind: String

    /** What the builder shows as the kind's name. */
    val label: String

    /** What the kind does, under its name on the builder's configure screen. */
    val description: String

    /** The settings the configure screen shows, in the order it shows them, options named with [names]. */
    fun fields(names: GameNames): List<StepField>

    /** The step in a few words, for the status lines. */
    fun summary(settings: StepSettings): String

    /** What the builder's slot shows for this kind, which may follow the settings (make shows its recipe's skill). */
    fun icon(settings: StepSettings): StepIcon

    /** The skill whose level the configure screen shows next to the kind's name, null for none. */
    fun skill(settings: StepSettings): Int? = null

    /** The setting the step mainly picks and the options it offers, named with [names]; null for a kind with none. */
    fun target(names: GameNames): StepTarget? = null

    /** The lines the builder's slot shows under the target (how much, from where), [context] what the steps before set up. */
    fun details(settings: StepSettings, context: FlowContext): List<String> = emptyList()

    /** Checks [settings] against the data and what the steps before it set up; throws [FlowError]. */
    fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep
}

/** The configure screen's two columns: the mockup's settings on the left, where the step works on the right. */
enum class FieldColumn { LEFT, RIGHT }

/** A setting of a step on the builder's configure screen, on a row under [label]. */
sealed interface StepField {

    val label: String

    val column: FieldColumn

    /** The step's [target], picked in the chatbox search headed [title]. */
    class Search(override val label: String, val target: StepTarget, val title: String) : StepField {
        override val column = FieldColumn.LEFT
    }

    /**
     * A whole number from [range] typed on the client's "Enter amount" prompt, kept under [key]; [rule] is what the
     * chat box says of a number out of range. [shown] words the value kept, null for none. [unbounded] is the word on
     * the button that removes the value, null when the setting has no such button.
     */
    class Typed(
        val key: String,
        override val label: String,
        val range: IntRange,
        val rule: String,
        val shown: (String?) -> String,
        val unbounded: String? = null,
        override val column: FieldColumn = FieldColumn.LEFT,
    ) : StepField

    /** A tile picked on the world map, kept under [key] as [Tile.text]. */
    class MapTile(val key: String, override val label: String) : StepField {
        override val column = FieldColumn.LEFT
    }

    /** What the step works out by itself, worded by [text] from its settings and what the steps before it set up. */
    class Note(override val label: String, val text: (StepSettings, FlowContext) -> String) : StepField {
        override val column = FieldColumn.LEFT
    }
}

/** Where an action step works: around the tile the player pressed Run on, or where a walk step before it went. */
sealed interface WorkSpot {

    data object RunTile : WorkSpot

    data class At(val tile: Tile) : WorkSpot

    fun tile(runTile: Tile): Tile =
        when (this) {
            RunTile -> runTile
            is At -> tile
        }
}

/**
 * What a step can rely on from the steps before it in the flow: where they work, the item ids they get, and the npcs
 * the last fight step fights (a pick-up step offers their drops). [gatheredBy] numbers the step (from 1) that first gets
 * each gathered id; the resolver fills it in, so the builder can say where a step's input comes from.
 */
data class FlowContext(
    val workSpot: WorkSpot = WorkSpot.RunTile,
    val gathered: Set<Int> = emptySet(),
    val fought: Set<Int> = emptySet(),
    val gatheredBy: Map<Int, Int> = emptyMap(),
) {
    /** The numbers of the steps that get any of [items], in flow order. */
    fun stepsGathering(items: Set<Int>): List<Int> = gatheredBy.filterKeys { it in items }.values.distinct().sorted()
}

/** The builder's slot line saying which earlier steps a processing step takes [what] from: "Logs from step 2". */
object StepInput {

    fun detail(what: String, context: FlowContext, items: Set<Int>): String {
        val steps = context.stepsGathering(items)
        return when (steps.size) {
            0 -> "No ${what.lowercase()} before it"
            1 -> "$what from step ${steps.single()}"
            else -> "$what from steps ${steps.joinToString(", ")}"
        }
    }
}

/** A step checked against the data: every name became the thing it names, so it can run. */
interface ResolvedStep {

    /** What the steps after this one can rely on. */
    fun after(context: FlowContext): FlowContext = context

    /** The activity that carries this step out for [player], whose flow was started on [runTile]. */
    fun activity(player: Player, runTile: Tile): StepActivity
}
