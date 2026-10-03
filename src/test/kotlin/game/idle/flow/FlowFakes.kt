package game.idle.flow

import game.idle.location.Tile
import io.luna.game.model.mob.Player

/** A resolved step that names itself; whatever it lists in [gathers] the steps after it can rely on. */
data class FakeStep(val name: String, val gathers: Set<Int> = emptySet()) : ResolvedStep {

    override fun after(context: FlowContext): FlowContext = context.copy(gathered = context.gathered + gathers)

    override fun activity(player: Player, runTile: Tile): StepActivity = error("fake steps start through FakeFlowPlayer")
}

/**
 * `<keyword> [<word>]`: the optional word is the step's one value. Resolving records the context it was given,
 * refuses the word "bad", and gathers item 1 when the word is "gather".
 */
class FakeStepType(override val keyword: String, override val fields: List<StepField> = emptyList()) : StepType {

    val contexts = mutableListOf<FlowContext>()

    override val label = "$keyword label"

    override val usage = "$keyword [<word>]"

    override fun parse(words: List<String>): List<String> {
        if (words.size > 1) throw FlowError("$keyword takes one word at most")
        return words
    }

    override fun line(values: List<String>): String = (listOf(keyword) + values).joinToString(" ")

    override fun resolve(values: List<String>, context: FlowContext): ResolvedStep {
        contexts += context
        if (values == listOf("bad")) throw FlowError("'bad' is refused")
        return FakeStep(line(values), gathers = if (values == listOf("gather")) setOf(1) else emptySet())
    }
}

/** Starts a [FakeStepActivity] named after each [FakeStep] and records the saved step indexes. */
class FakeFlowPlayer : FlowPlayer {

    val savedSteps = mutableListOf<Int>()
    val log = mutableListOf<String>()
    val started = mutableListOf<FakeStepActivity>()

    override fun activity(step: ResolvedStep): StepActivity =
        FakeStepActivity((step as FakeStep).name, log).also { started += it }

    override fun saveStep(index: Int) {
        savedSteps += index
    }
}

/** Records acts as `<name>:<n>`; [done] ends the step. */
class FakeStepActivity(private val name: String, val log: MutableList<String>) : StepActivity {

    var busy = false
    var done = false
    private var acts = 0

    override fun isBusy(): Boolean = busy

    override fun isDone(): Boolean = done

    override fun act() {
        acts++
        log += "$name:$acts"
    }
}
