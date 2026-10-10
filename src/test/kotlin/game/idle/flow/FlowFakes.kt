package game.idle.flow

import game.idle.flow.option.GameNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.StepTarget
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/**
 * A resolved step that names itself; whatever it lists in [gathers] the steps after it can rely on, and it needs what
 * [needs] lists.
 */
data class FakeStep(val name: String, val gathers: Set<Int> = emptySet(), val needs: List<StepNeeds> = emptyList()) : ResolvedStep {

    override fun after(context: FlowContext): FlowContext = context.copy(gathered = context.gathered + gathers)

    override fun needs(): List<StepNeeds> = needs

    override fun activity(player: Player, runTile: Tile): StepActivity = error("fake steps start through FakeFlowPlayer")
}

/**
 * A kind of step with one optional setting, [WORD], the configure screen's [fields], and optionally a [skill] and a
 * [target]. Resolving records the context it was given, refuses the word "bad", and gathers item 1 when the word is
 * "gather"; with [resolved] it resolves to what that gives instead.
 */
class FakeStepType(
    override val kind: String,
    private val fields: List<StepField> = emptyList(),
    private val skill: Int? = null,
    private val target: StepTarget? = null,
    private val newStep: ((FlowContext) -> StepSettings)? = null,
    private val inputOf: ((StepSettings) -> InputSource)? = null,
    private val resolved: ((StepSettings) -> ResolvedStep)? = null,
) : StepType {

    val contexts = mutableListOf<FlowContext>()

    override val label = "$kind label"

    override val description = "Does $kind things."

    override fun fields(names: GameNames): List<StepField> = fields

    override fun summary(settings: StepSettings): String = listOfNotNull(kind, settings[WORD]).joinToString(" ")

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Media(kind, 0)

    override fun skill(settings: StepSettings): Int? = skill

    override fun target(names: GameNames): StepTarget? = target

    override fun newSettings(before: FlowContext): StepSettings = newStep?.invoke(before) ?: super.newSettings(before)

    override fun input(settings: StepSettings, before: FlowContext): InputSource = inputOf?.invoke(settings) ?: super.input(settings, before)

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        contexts += context
        if (settings[WORD] == "bad") throw FlowError("'bad' is refused")
        resolved?.let { return it(settings) }
        return FakeStep(summary(settings), gathers = if (settings[WORD] == "gather") setOf(1) else emptySet())
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

    override fun tell(message: String) {
        told += message
    }

    override fun activity(step: ResolvedStep): StepActivity =
        FakeStepActivity((step as FakeStep).name, log).also { started += it }

    override fun saveStep(index: Int) {
        savedSteps += index
    }

    override fun lapCompleted() {
        laps++
    }
}

/** Records acts as `<name>:<n>`; [done] ends the step, [amountDone] is what its Amount counts so far. */
class FakeStepActivity(private val name: String, val log: MutableList<String>) : StepActivity, CountsAmount {

    var busy = false
    var done = false
    var stop: String? = null
    var blocked: String? = null
    var amountDone = 0
    private var acts = 0

    override fun amountDone(): Int = amountDone

    override fun isBusy(): Boolean = busy

    override fun stopReason(): String? = stop

    override fun blocked(): String? = blocked

    override fun isDone(): Boolean = done

    override fun act() {
        acts++
        log += "$name:$acts"
    }
}

/** A kind's configure fields in words, so a test compares them at a glance. */
fun described(fields: List<StepField>): List<String> =
    fields.map { field ->
        val words = when (field) {
            is StepField.Search -> "search ${field.target.key}, '${field.title}'"
            is StepField.Typed -> "typed ${field.key} ${field.range}" + (field.unbounded?.let { ", button '$it'" } ?: "")
            is StepField.MapTile -> "map ${field.key}"
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
    var canFlee = true
    var dead = false
    val done = mutableListOf<String>()

    override fun health(): Health = health

    override fun foodSlot(foods: Set<Int>): Int? =
        bag.entries.sortedBy { it.key }.firstOrNull { (_, id) -> if (foods.isEmpty()) id != INEDIBLE else id in foods }?.key

    override fun eat(slot: Int) {
        done += "eat $slot"
    }

    override fun underAttack(): Boolean = attacked

    override fun flee(): Boolean {
        done += "flee"
        return canFlee
    }

    override fun dead(): Boolean = dead

    companion object {
        const val INEDIBLE = 1511
    }
}
