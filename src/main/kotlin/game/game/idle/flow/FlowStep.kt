package game.idle.flow

import game.idle.flow.option.GameNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.OptionFacts
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepTarget
import game.idle.location.Area
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/** A step of a flow the player could not have meant; the message is shown as is. */
class FlowError(override val message: String) : RuntimeException(message)

/**
 * One step of a flow as the player set it up: its kind and its settings by name, as text. This is what a save
 * keeps, so it stays Gson-friendly (every field has a default) and a setting a step has no value for reads as its
 * default: settings added later need no conversion of older saves. An empty value is never kept. [id] stays put while
 * the steps move, so a reflex's jump follows its step (0 until the flow gives it one, [FlowIds]); [reflexes] are the
 * ids of the flow's reflexes attached to the step, in the order it checks them (S07c).
 */
data class StepSettings(
    val kind: String = "",
    val values: Map<String, String> = emptyMap(),
    val id: Int = 0,
    val reflexes: List<Int> = emptyList(),
) {

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
data class StepPick(val label: String?, val icon: OptionIcon?) {

    companion object {
        /**
         * Several items picked, named with [names]: "Oak logs, willow logs" with the first one's picture, each name once (a
         * pick-up step gathers every item named "Bones"); no label for none.
         */
        fun items(ids: List<Int>, names: GameNames): StepPick {
            val label = ids.map(names::item).distinct().mapIndexed { index, name -> if (index == 0) name else name.lowercase() }.joinToString(", ")
            return StepPick(label.ifEmpty { null }, ids.firstOrNull()?.let(OptionIcon::Item))
        }
    }
}

/**
 * A setting of a step on the builder's configure screen, on a row under [label], taking [rows] rows; [visible] says
 * whether the settings show it (Deposit's Chosen list only on Chosen).
 */
sealed interface StepField {

    val label: String

    val column: FieldColumn

    val rows: Int get() = 1

    val visible: (StepSettings) -> Boolean

    /**
     * The step's [target], picked in the chatbox search headed [title]; [missing] words a value no option has any more
     * (a jump's deleted step), which otherwise shows as it is kept.
     */
    class Search(
        override val label: String,
        val target: StepTarget,
        val title: String,
        override val visible: (StepSettings) -> Boolean = ALWAYS,
        val missing: String? = null,
    ) : StepField {
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
    ) : StepField {
        override val visible = ALWAYS
    }

    /**
     * A tile picked on the world map, kept under [key] as [Tile.text]. A field that may hold none words it [none], and
     * the row's x button puts it back (S07e's Work spot: "From the flow"); without [none] the step needs a tile.
     */
    class MapTile(
        val key: String,
        override val label: String,
        override val column: FieldColumn = FieldColumn.LEFT,
        val none: String? = null,
    ) : StepField {
        override val visible = ALWAYS
    }

    /** What the step works out by itself, worded by [text] from its settings and what the steps before it set up. */
    class Note(
        override val label: String,
        override val column: FieldColumn = FieldColumn.LEFT,
        override val visible: (StepSettings) -> Boolean = ALWAYS,
        val text: (StepSettings, FlowContext) -> String,
    ) : StepField

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
        override val visible: (StepSettings) -> Boolean = ALWAYS,
    ) : StepField

    /**
     * Several items of [source] kept under [key] ([StepItems]), shown as a list on [rows] rows (Maxime, 2026-10-10): its
     * first line, worded [add] ([empty] while the list holds nothing, when given), opens the search headed [title], which
     * stays open and adds or takes out each row clicked. With [amounts] each line has its amount too, typed or All (a
     * withdrawal, S07b).
     */
    class Items(
        val key: String,
        override val label: String,
        val source: OptionSource,
        val title: String,
        val add: String,
        override val rows: Int,
        override val column: FieldColumn = FieldColumn.LEFT,
        val amounts: Boolean = false,
        override val visible: (StepSettings) -> Boolean = ALWAYS,
        val empty: String? = null,
    ) : StepField

    private companion object {
        val ALWAYS: (StepSettings) -> Boolean = { true }
    }
}

/** A button of a [StepField.Toggle]: the [value] it keeps, under its [word]. */
data class Choice(val value: String, val word: String)

/**
 * Where an action step works: around the tile the player pressed Run on, where the flow went before it (a walk step's
 * tile or an earlier step's own spot), or a tile picked for the step itself (S07e).
 */
sealed interface WorkSpot {

    data object RunTile : WorkSpot

    data class At(val tile: Tile) : WorkSpot

    data class Own(val tile: Tile) : WorkSpot

    /** The spot's tile, null for the Run tile, which is not known before Run. */
    fun known(): Tile? =
        when (this) {
            RunTile -> null
            is At -> tile
            is Own -> tile
        }

    fun tile(runTile: Tile): Tile = known() ?: runTile

    /** What the steps after one working here rely on: a spot of its own is where the flow went (Maxime, 2026-10-10). */
    fun after(context: FlowContext): FlowContext = if (this is Own) context.copy(workSpot = At(tile)) else context

    /** The area a step working [radius] tiles around its own spot works in, null when the spot is not its own. */
    fun ownArea(radius: Int): Area? = (this as? Own)?.let { Area(it.tile, radius) }
}

/**
 * What a step can rely on from the steps before it in the flow: where the flow went, the item ids they get, and the npcs
 * the last fight step fights (a pick-up step offers their drops). [gatheredBy] numbers the step (from 1) that first gets
 * each gathered id; the resolver fills it in, so the builder can say where a step's input comes from. [lap] is what
 * every step of the flow gathers or makes, which the bank's Gathered deposits since the flow loops (S07b).
 */
data class FlowContext(
    val workSpot: WorkSpot = WorkSpot.RunTile,
    val gathered: Set<Int> = emptySet(),
    val fought: Set<Int> = emptySet(),
    val gatheredBy: Map<Int, Int> = emptyMap(),
    val lap: Set<Int> = emptySet(),
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

    /**
     * What the step needs in the bag, one entry per way it can work (a make recipe's ways), none for a step that needs
     * nothing; the configure screen warns about the first way the flow cannot supply (S07b).
     */
    fun needs(): List<StepNeeds> = emptyList()

    /** The activity that carries this step out for [player], whose flow was started on [runTile]. */
    fun activity(player: Player, runTile: Tile): StepActivity
}

/** A step that fights: its configure screen warns when no reflex attached to it eats or runs (S07c). */
interface Fights

/**
 * A step that can work at a spot picked for it (S07e): [ownArea] is the area around that tile it works in, null when it
 * works where the flow went. From outside that area the step walks to the tile before it works (Maxime, 2026-10-11).
 */
interface OwnSpot {

    val ownArea: Area?
}

/** A kind of step whose configure screen has warnings of its own, besides tools and inputs (S07d: a pick-up step's items). */
interface OwnWarnings {

    /** The warnings, which never stop the flow, for [settings] after what the steps [before] it set up, the player as [facts] say. */
    fun warnings(settings: StepSettings, before: FlowContext, facts: OptionFacts): List<String>
}

/**
 * What a step needs in the bag to work one way: its [tools] (kept), and [inputs] (item ids used up), which a step taking
 * its input from the bank relies on a bank step to withdraw.
 */
data class StepNeeds(val tools: List<ToolNeed> = emptyList(), val inputs: List<Int> = emptyList())

/**
 * A tool or bait a step needs carried or worn: any one of [items], an item id to the level in [skill] it needs (no skill:
 * any level). [word] names it in a warning ("axe"), null for the first item's own name; a [countable] one is "one", bait
 * "any" ("no bank step withdraws any").
 */
data class ToolNeed(val word: String?, val items: Map<Int, Int>, val skill: Int? = null, val countable: Boolean = true)

/** A step that moves items between the bag and the bank: the ids it [withdraws], and whether it banks [id] from the bag. */
interface BankMoves {

    val withdraws: Set<Int>

    fun banks(id: Int): Boolean
}
