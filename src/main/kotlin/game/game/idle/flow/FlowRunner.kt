package game.idle.flow

import game.idle.autopilot.AutopilotActivity

/** An activity that knows when its step is over. */
interface StepActivity : AutopilotActivity {

    fun isDone(): Boolean
}

/** The player a flow runs for: where each step's activity comes from and where its progress is saved. */
interface FlowPlayer {

    fun activity(step: ResolvedStep): StepActivity

    fun saveStep(index: Int)

    /** The flow finished its last step and starts over. */
    fun lapCompleted()
}

/**
 * Runs a flow step by step: each tick the current step's activity acts until it says it is done, then the next one
 * starts, and after the last step the first one again, which counts as a lap. The step index is saved whenever it
 * changes so a relog resumes where it was.
 */
class FlowRunner(
    private val steps: List<ResolvedStep>,
    startIndex: Int,
    private val player: FlowPlayer,
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
        val started = player.activity(steps[index])
        current = started
        return started
    }

    private fun advance() {
        current = null
        index = (index + 1) % steps.size
        if (index == 0) player.lapCompleted()
        player.saveStep(index)
    }
}
