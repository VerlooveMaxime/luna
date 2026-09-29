package game.idle.flow

import game.idle.autopilot.AutopilotActivity
import game.idle.autopilot.woodcutting.ChopAction

/** An activity that knows when its step is over. */
interface StepActivity : AutopilotActivity {

    fun isDone(): Boolean
}

/** Builds the activity for each kind of step; the Luna one wires real players in. */
interface StepActivities {

    fun chop(step: ResolvedStep.Chop): StepActivity

    fun bank(step: ResolvedStep.Bank): StepActivity
}

/** What the runner reads and writes on the player besides what the step activities do. */
interface FlowPlayer {

    fun woodcuttingLevel(): Int

    fun inventoryFull(): Boolean

    /** In the inventory and the bank together. */
    fun countOwned(itemId: Int): Int

    fun saveStep(index: Int)

    fun tell(message: String)
}

/**
 * Runs a flow step by step: each tick the current step's activity acts until the step is over, then the next one
 * starts. `loop` goes back to the first step; running off the end calls [onFinished]. The step index is saved
 * whenever it changes so a relog resumes where it was.
 */
class FlowRunner(
    private val steps: List<ResolvedStep>,
    startIndex: Int,
    private val player: FlowPlayer,
    private val activities: StepActivities,
    private val onFinished: () -> Unit,
) : AutopilotActivity {

    private var index = startIndex.coerceIn(0, steps.size)
    private var current: StepActivity? = null
    private var finished = false

    override fun isBusy(): Boolean = current?.isBusy() ?: false

    override fun act() {
        if (finished) return
        if (index >= steps.size) return finish()
        val activity = current ?: startStep()
        if (activity == null || stepOver(activity)) {
            advance()
            return
        }
        activity.act()
    }

    /** Null for a step with no activity of its own (`loop`). */
    private fun startStep(): StepActivity? {
        val started = when (val step = steps[index]) {
            is ResolvedStep.Chop -> activities.chop(step)
            is ResolvedStep.Bank -> activities.bank(step)
            ResolvedStep.Loop -> null
        }
        current = started
        return started
    }

    private fun stepOver(activity: StepActivity): Boolean {
        val step = steps[index]
        return activity.isDone() || step is ResolvedStep.Chop && step.until?.let { holds(it, step.action) } == true
    }

    private fun holds(until: Until, action: ChopAction): Boolean =
        when (until) {
            Until.InventoryFull -> player.inventoryFull()
            is Until.Logs -> action.trees.sumOf { player.countOwned(it.logId) } >= until.count
            is Until.Level -> player.woodcuttingLevel() >= until.level
        }

    private fun advance() {
        current = null
        index = if (steps[index] == ResolvedStep.Loop) 0 else index + 1
        player.saveStep(index)
        if (index >= steps.size) finish()
    }

    private fun finish() {
        finished = true
        player.tell("Autopilot: flow finished.")
        onFinished()
    }
}
