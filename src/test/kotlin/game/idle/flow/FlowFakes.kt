package game.idle.flow

import game.idle.flow.option.FakeNames
import game.idle.flow.option.GameNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.StepTarget
import game.idle.font.EVEN_FONT
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/**
 * A resolved step that names itself; whatever it lists in [gathers] the steps after it can rely on, and it needs what
 * [needs] lists.
 */
data class FakeStep(val name: String, val gathers: Set<Int> = emptySet(), val needs: List<StepNeeds> = emptyList()) : ResolvedStep {

    override fun after(context: FlowContext): FlowContext = context.copy(gathered = context.gathered + gathers)

    override fun needs(): List<StepNeeds> = needs

    override fun work(player: Player, runTile: Tile): StepWork = FakeStepWork(StepOutcome.Finished)

    override fun toString(): String = name
}

/**
 * A kind of step with one optional setting, [WORD], the configure screen's [fields], and optionally a [skill], a
 * [target] and the slot's [detailsOf] its settings. Resolving records the context it was given, refuses the word "bad",
 * names its [FakeStep] after its kind and word, and gathers item 1 when the word is "gather"; with [resolved] it
 * resolves to what that gives instead.
 */
class FakeStepType(
    override val kind: String,
    private val fields: List<StepField> = emptyList(),
    private val skill: Int? = null,
    private val target: StepTarget? = null,
    private val newStep: ((FlowContext) -> StepSettings)? = null,
    private val inputOf: ((StepSettings) -> InputSource)? = null,
    private val resolved: ((StepSettings) -> ResolvedStep)? = null,
    private val icon: StepIcon = StepIcon.Media(kind, 0),
    private val detailsOf: ((StepSettings, FlowContext) -> List<String>)? = null,
    override val goal: Boolean = false,
) : StepType {

    val contexts = mutableListOf<FlowContext>()

    override val label = "$kind label"

    override val description = "Does $kind things."

    override fun fields(names: GameNames): List<StepField> = fields

    override fun icon(settings: StepSettings): StepIcon = icon

    override fun skill(settings: StepSettings): Int? = skill

    override fun target(names: GameNames): StepTarget? = target

    override fun newSettings(before: FlowContext): StepSettings = newStep?.invoke(before) ?: super.newSettings(before)

    override fun input(settings: StepSettings, before: FlowContext): InputSource = inputOf?.invoke(settings) ?: super.input(settings, before)

    override fun details(settings: StepSettings, context: FlowContext): List<String> = detailsOf?.invoke(settings, context) ?: super.details(settings, context)

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        contexts += context
        if (settings[WORD] == "bad") throw FlowError("'bad' is refused")
        resolved?.let { return it(settings) }
        return FakeStep(listOfNotNull(kind, settings[WORD]).joinToString(" "), gathers = if (settings[WORD] == "gather") setOf(1) else emptySet())
    }

    companion object {
        const val WORD = "word"

        /** A step of [kind] with [word] as its setting, or none. */
        fun step(kind: String, word: String? = null): StepSettings =
            StepSettings(kind, word?.let { mapOf(WORD to it) } ?: emptyMap())
    }
}

/** Starts a [FakeStepActivity] named after each [FakeStep] and records the saved step indexes and what it was told. */
class FakeFlowPlayer(override val body: FakeReflexBody = FakeReflexBody()) : FlowPlayer {

    val savedSteps = mutableListOf<Int>()
    var laps = 0
    val log = mutableListOf<String>()
    val told = mutableListOf<String>()
    val started = mutableListOf<FakeStepActivity>()
    val savedProgress = mutableListOf<RunProgress>()
    val levels = mutableMapOf<Int, Int>()
    val bank = mutableMapOf<Int, Int>()

    /** The bank steps started, each with the withdrawals it was asked to leave out. */
    val leftOut = mutableListOf<Set<Int>>()

    override fun tell(message: String) {
        told += message
    }

    override fun activity(step: ResolvedStep): StepActivity {
        if (step is FakeBankStep) leftOut += step.leftOut
        return FakeStepActivity(step.toString(), log).also { started += it }
    }

    override fun saveStep(index: Int) {
        savedSteps += index
    }

    override fun saveProgress(progress: RunProgress) {
        savedProgress += progress
    }

    override fun lapCompleted() {
        laps++
    }

    override fun level(skill: Int): Int = levels[skill] ?: 1

    override fun banked(items: Set<Int>): Int = items.sumOf { bank[it] ?: 0 }
}

/** A bank step that withdraws [withdraws] and remembers what it was told to leave out ([leftOut]). */
data class FakeBankStep(override val withdraws: Set<Int>, val leftOut: Set<Int> = emptySet()) : ResolvedStep, BankMoves {

    override fun banks(id: Int): Boolean = false

    override fun without(items: Set<Int>): ResolvedStep = copy(leftOut = items)

    override fun work(player: Player, runTile: Tile): StepWork = FakeStepWork(StepOutcome.Finished)

    override fun toString(): String = "bank"
}

/** Words over [EVEN_FONT], where every glyph is 5 px wide, so widths are easy to reckon in a test. */
fun words(names: GameNames = FakeNames()): StepWords = StepWords(names, EVEN_FONT)

/**
 * Records acts as `<name>:<n>`; [done] ends the step, [amountDone] is what its Amount counts so far, [said] is given
 * once as a real step gives it, [ended] counts the turns ended early. It counts as having [worked] unless a test says
 * otherwise, so flows lap as they did before the idle-lap stop.
 */
class FakeStepActivity(private val name: String, val log: MutableList<String>) : StepActivity {

    var busy = false
    var done = false
    var stop: StepStop? = null
    var blocked: Reason? = null
    var waiting: Reason? = null
    var said: Said? = null
    var amountDone = 0
    var worked = true
    var ended = 0
    private var acts = 0

    override fun amountDone(): Int = amountDone

    override fun isBusy(): Boolean = busy

    override fun stopping(): StepStop? = stop

    override fun blocked(): Reason? = blocked

    override fun waiting(): Reason? = waiting

    override fun said(): Said? = said.also { said = null }

    override fun isDone(): Boolean = done

    override fun worked(): Boolean = worked

    override fun endTurn() {
        ended++
        log += "$name:end"
    }

    override fun act() {
        acts++
        log += "$name:$acts"
    }
}

/**
 * A kind's work for the turn's own tests: each act gives the next of [outcomes] (the last one again once they run
 * out), [done] records what it was asked, [reading] is what its meter reads.
 */
class FakeStepWork(vararg outcomes: StepOutcome, meter: ((() -> Int) -> Meter)? = Meter::Rises) : StepWork {

    private val outcomes = outcomes.toList()
    val done = mutableListOf<String>()
    var busy = false
    var finishing = false
    var reading = 0
    private var acts = 0

    override val meter: Meter = meter?.invoke { reading } ?: Meter.None

    override fun isBusy(): Boolean = busy

    override fun finishing(): Boolean = finishing

    override fun interrupt() {
        done += "interrupt"
    }

    override fun act(left: Int?): StepOutcome {
        done += "act $left"
        return outcomes[minOf(acts++, outcomes.lastIndex)]
    }
}

/** What the player has on them in a test: the level in [levels] (1 without one), the [bag] and [worn] item ids, [full]. */
class FakeCarried : Carried {

    val levels = mutableMapOf<Int, Int>()
    val bag = mutableSetOf<Int>()
    val worn = mutableSetOf<Int>()
    var full = false

    override fun level(skill: Int): Int = levels[skill] ?: 1

    override fun holds(id: Int): Boolean = id in bag

    override fun wears(id: Int): Boolean = id in worn

    override fun bagFull(): Boolean = full
}

/** A kind's configure fields in words, so a test compares them at a glance. */
fun described(fields: List<StepField>): List<String> =
    fields.map { field ->
        val words = when (field) {
            is StepField.Search -> "search ${field.target.key}, '${field.title}'"
            is StepField.Typed -> "typed ${field.key} ${field.range}" + (field.unbounded?.let { ", button '$it'" } ?: "")
            is StepField.MapTile -> "map ${field.key}" + (field.none?.let { ", none '$it'" } ?: "")
            is StepField.Note -> "note"
            is StepField.Toggle -> "toggle ${field.key} ${field.choices.joinToString(" / ") { "${it.value} '${it.word}'" }}"
            is StepField.Items -> "list ${field.key} on ${field.rows} rows, '${field.title}'"
        }
        "${field.label} (${field.column.name.lowercase()}): $words"
    }

/** A player's body for reflexes: [bag] maps slots to item ids, each but [INEDIBLE] a food; what it does is kept as text ("eat 3"). */
class FakeReflexBody(var health: Health = Health(20, 20)) : ReflexBody {

    val bag = mutableMapOf<Int, Int>()
    var attacked = false
    var dead = false
    val done = mutableListOf<String>()

    override fun health(): Health = health

    override fun foodSlot(foods: Set<Int>): Int? =
        bag.entries.sortedBy { it.key }.firstOrNull { (_, id) -> if (foods.isEmpty()) id != INEDIBLE else id in foods }?.key

    override fun eat(slot: Int) {
        done += "eat $slot"
    }

    override fun underAttack(): Boolean = attacked

    override fun flee() {
        done += "flee"
    }

    override fun calm() {
        done += "calm"
    }

    override fun dead(): Boolean = dead

    companion object {
        const val INEDIBLE = 1511
    }
}
