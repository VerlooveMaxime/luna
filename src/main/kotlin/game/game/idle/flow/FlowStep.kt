package game.idle.flow

import game.idle.flow.option.GameNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.OptionSource
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

    /**
     * What the builder shows for what the step picked, its label and picture: by default its [target]'s option, null for
     * a kind that picks nothing.
     */
    fun pick(settings: StepSettings, names: GameNames): StepPick? =
        target(names)?.let { target ->
            val option = target.picked(settings)
            StepPick(option?.label ?: settings[target.key], option?.icon)
        }

    /** The settings a new step of this kind starts with, [before] being what the steps before it set up. */
    fun newSettings(before: FlowContext): StepSettings = StepSettings(kind)

    /** Where the step takes what it works on, which narrows its searches (S01); the bank for a kind that takes nothing. */
    fun input(settings: StepSettings, before: FlowContext): InputSource = InputSource.BANK

    /** The lines the builder's slot shows under the target (how much, from where), [context] what the steps before set up. */
    fun details(settings: StepSettings, context: FlowContext): List<String> = emptyList()

    /** Checks [settings] against the data and what the steps before it set up; throws [FlowError]. */
    fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep
}

/** The configure screen's two columns: the mockup's settings on the left, where the step works on the right. */
enum class FieldColumn { LEFT, RIGHT }

/** What a step picked as the builder shows it: [label] (null when nothing is picked yet) and [icon]. */
data class StepPick(val label: String?, val icon: OptionIcon?)

/** A setting of a step on the builder's configure screen, on a row under [label], taking [rows] rows. */
sealed interface StepField {

    val label: String

    val column: FieldColumn

    val rows: Int get() = 1

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

    /**
     * One of [choices] kept under [key], picked from a row of small buttons; [current] is the one lit for the settings
     * and what the steps before set up.
     */
    class Toggle(
        val key: String,
        override val label: String,
        val choices: List<Choice>,
        val current: (StepSettings, FlowContext) -> String,
        override val column: FieldColumn = FieldColumn.LEFT,
    ) : StepField

    /**
     * Several items of [source] kept under [key] ([StepItems]), shown as a list on [rows] rows (Maxime, 2026-10-10): its
     * first line, worded [add], opens the search headed [title], which stays open and adds or takes out each row clicked.
     */
    class Items(
        val key: String,
        override val label: String,
        val source: OptionSource,
        val title: String,
        val add: String,
        override val rows: Int,
        override val column: FieldColumn = FieldColumn.LEFT,
    ) : StepField
}

/** A button of a [StepField.Toggle]: the [value] it keeps, under its [word]. */
data class Choice(val value: String, val word: String)

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

/**
 * The builder's slot line saying where a processing step takes [what] from: "from the bank", or the earlier steps that
 * get [items], "from step 2" ("No logs before it" when none does).
 */
object StepInput {

    fun detail(what: String, input: InputSource, context: FlowContext, items: Set<Int>): String {
        if (input == InputSource.BANK) return "from the bank"
        val steps = context.stepsGathering(items)
        return when (steps.size) {
            0 -> "No ${what.lowercase()} before it"
            1 -> "from step ${steps.single()}"
            else -> "from steps ${steps.joinToString(", ")}"
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
