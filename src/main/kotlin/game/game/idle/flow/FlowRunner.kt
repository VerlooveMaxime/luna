package game.idle.flow

import game.idle.autopilot.AutopilotActivity

/** An activity that knows when its step is over. */
interface StepActivity : AutopilotActivity {

    fun isDone(): Boolean
}

/** Builds the activity for each kind of step; the Luna one wires real players in. */
interface StepActivities {

    fun chop(step: ResolvedStep.Chop): StepActivity

    fun drop(step: ResolvedStep.Drop): StepActivity

    fun bank(step: ResolvedStep.Bank): StepActivity
}

/** What the runner writes on the player besides what the step activities do. */
interface FlowPlayer {

    fun saveStep(index: Int)
}

/**
 * Runs a flow step by step: each tick the current step's activity acts until it says it is done, then the next one
 * starts, and after the last step the first one again. The step index is saved whenever it changes so a relog
 * resumes where it was.
 */
class FlowRunner(
    private val steps: List<ResolvedStep>,
    startIndex: Int,
    private val player: FlowPlayer,
    private val activities: StepActivities,
) : AutopilotActivity {

    private var index = if (steps.isEmpty()) 0 else startIndex.coerceIn(0, steps.lastIndex)
    private var current: StepActivity? = null

    override fun isBusy(): Boolean = current?.isBusy() ?: false

    override fun act() {
        if (steps.isEmpty()) return
        val activity = current ?: startStep()
        if (activity.isDone()) {
            advance()
            return
        }
        activity.act()
    }

    private fun startStep(): StepActivity {
        val started = when (val step = steps[index]) {
            is ResolvedStep.Chop -> activities.chop(step)
            is ResolvedStep.Drop -> activities.drop(step)
            is ResolvedStep.Bank -> activities.bank(step)
        }
        current = started
        return started
    }

    private fun advance() {
        current = null
        index = (index + 1) % steps.size
        player.saveStep(index)
    }
}
