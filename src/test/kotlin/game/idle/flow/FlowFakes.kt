package game.idle.flow

import game.idle.flow.option.GameNames
import game.idle.flow.option.StepTarget
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/** A resolved step that names itself; whatever it lists in [gathers] the steps after it can rely on. */
data class FakeStep(val name: String, val gathers: Set<Int> = emptySet()) : ResolvedStep {

    override fun after(context: FlowContext): FlowContext = context.copy(gathered = context.gathered + gathers)

    override fun activity(player: Player, runTile: Tile): StepActivity = error("fake steps start through FakeFlowPlayer")
}

/**
 * A kind of step with one optional setting, [WORD], the configure screen's [fields], and optionally a [skill] and a
 * [target]. Resolving records the context it was given, refuses the word "bad", and gathers item 1 when the word is
 * "gather".
 */
class FakeStepType(
    override val kind: String,
    private val fields: List<StepField> = emptyList(),
    private val skill: Int? = null,
    private val target: StepTarget? = null,
) : StepType {

    val contexts = mutableListOf<FlowContext>()

    override val label = "$kind label"

    override val description = "Does $kind things."

    override fun fields(names: GameNames): List<StepField> = fields

    override fun summary(settings: StepSettings): String = listOfNotNull(kind, settings[WORD]).joinToString(" ")

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Media(kind, 0)

    override fun skill(settings: StepSettings): Int? = skill

    override fun target(names: GameNames): StepTarget? = target

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        contexts += context
        if (settings[WORD] == "bad") throw FlowError("'bad' is refused")
        return FakeStep(summary(settings), gathers = if (settings[WORD] == "gather") setOf(1) else emptySet())
    }

    companion object {
        const val WORD = "word"

        /** A step of [kind] with [word] as its setting, or none. */
        fun step(kind: String, word: String? = null): StepSettings =
            StepSettings(kind, word?.let { mapOf(WORD to it) } ?: emptyMap())
    }
}

/** Starts a [FakeStepActivity] named after each [FakeStep] and records the saved step indexes. */
class FakeFlowPlayer : FlowPlayer {

    val savedSteps = mutableListOf<Int>()
    var laps = 0
    val log = mutableListOf<String>()
    val started = mutableListOf<FakeStepActivity>()

    override fun activity(step: ResolvedStep): StepActivity =
        FakeStepActivity((step as FakeStep).name, log).also { started += it }

    override fun saveStep(index: Int) {
        savedSteps += index
    }

    override fun lapCompleted() {
        laps++
    }
}

/** Records acts as `<name>:<n>`; [done] ends the step. */
class FakeStepActivity(private val name: String, val log: MutableList<String>) : StepActivity {

    var busy = false
    var done = false
    var stop: String? = null
    var blocked: String? = null
    private var acts = 0

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
        }
        "${field.label} (${field.column.name.lowercase()}): $words"
    }
