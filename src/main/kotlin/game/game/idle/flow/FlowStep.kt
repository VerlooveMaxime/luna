package game.idle.flow

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

    /** What the builder's kind field shows. */
    val label: String

    /** The builder's fields, in the order it shows them. */
    val fields: List<StepField>

    /** The step in a few words, for the builder's rows and the status lines. */
    fun summary(settings: StepSettings): String

    /** What the builder's slot shows for this kind, which may follow the settings (make shows its recipe's skill). */
    fun icon(settings: StepSettings): StepIcon

    /** Checks [settings] against the data and what the steps before it set up; throws [FlowError]. */
    fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep
}

/** A setting of a step in the builder, kept under [key]. */
sealed interface StepField {

    val key: String

    val label: String

    /**
     * A setting that cycles through [choices] on click; they may depend on the step's other settings. A new step starts
     * at [default] when it is offered, else at the first choice. [display] is how the builder shows a value.
     */
    class Choice(
        override val key: String,
        override val label: String,
        val default: String? = null,
        val display: (String) -> String = { it },
        val choices: (settings: StepSettings) -> List<String>,
    ) : StepField

    /** A map tile, written as [Tile.text]; the builder fills it in with the player's own tile. */
    class MapTile(override val key: String, override val label: String) : StepField
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
 * the last fight step fights (a pick-up step offers their drops).
 */
data class FlowContext(
    val workSpot: WorkSpot = WorkSpot.RunTile,
    val gathered: Set<Int> = emptySet(),
    val fought: Set<Int> = emptySet(),
)

/** A step checked against the data: every name became the thing it names, so it can run. */
interface ResolvedStep {

    /** What the steps after this one can rely on. */
    fun after(context: FlowContext): FlowContext = context

    /** The activity that carries this step out for [player], whose flow was started on [runTile]. */
    fun activity(player: Player, runTile: Tile): StepActivity
}
