package game.idle.flow

import game.idle.autopilot.AutopilotActivity

/** An activity that knows when its step is over. */
interface StepActivity : AutopilotActivity {

    fun isDone(): Boolean
}

/** The player a flow runs for: where each step's activity comes from and where its progress is saved. */
interface FlowPlayer {

    /** What the flow's reflexes see and do for the player, and whether they died. */
    val body: ReflexBody

    fun activity(step: ResolvedStep): StepActivity

    fun saveStep(index: Int)

    /** The flow finished its last step and starts over. */
    fun lapCompleted()

    fun tell(message: String)
}

/** A flow resolved: its [steps] and, for each, the reflexes attached to it in its order (S07c). */
data class ResolvedFlow(val steps: List<ResolvedStep>, val reflexes: List<List<ResolvedReflex>> = steps.map { emptyList() }) {

    init {
        require(reflexes.size == steps.size) { "Each of the ${steps.size} steps needs its reflexes, not ${reflexes.size} lists" }
    }
}

/**
 * Runs a flow step by step: each tick the current step's activity, guarded by its reflexes, acts until it says it is
 * done, then the next one starts, and after the last step the first one again, which counts as a lap. A reflex's jump
 * goes on from the step it names and never counts a lap (Maxime, 2026-10-10); a step cut short by a jump and cut short
 * again on its next turn before it made progress stops the flow, as does the player's death. The step index is saved
 * whenever it changes so a relog resumes where it was.
 */
class FlowRunner(
    private val flow: ResolvedFlow,
    startIndex: Int,
    private val player: FlowPlayer,
) : AutopilotActivity {

    private val steps = flow.steps
    private var index = if (steps.isEmpty()) 0 else startIndex.coerceIn(0, steps.lastIndex)
    private var current: ReflexGuard? = null
    private var stopReason: String? = null

    /** The steps a jump cut short on their last turn. */
    private val cutShort = mutableSetOf<Int>()

    override fun isBusy(): Boolean = current?.isBusy() ?: false

    override fun stopReason(): String? = if (player.body.dead()) DIED else stopReason ?: current?.stopReason()

    /** The current step's block; none between two steps. */
    override fun blocked(): String? = current?.blocked()

    override fun act() {
        if (steps.isEmpty()) return
        val activity = current ?: startStep()
        if (activity.isDone()) {
            advance()
            return
        }
        activity.act()
        activity.jump()?.let { jump(activity, it) }
    }

    private fun startStep(): ReflexGuard {
        val started = ReflexGuard(player.activity(steps[index]), flow.reflexes[index], player.body)
        current = started
        return started
    }

    private fun advance() {
        cutShort -= index
        current = null
        index = (index + 1) % steps.size
        if (index == 0) player.lapCompleted()
        player.saveStep(index)
    }

    private fun jump(activity: ReflexGuard, jump: ReflexOutcome.Jump) {
        if (index in cutShort && activity.progress() == 0) {
            stopReason = "Autopilot: stopped, step ${index + 1} was cut short again before it made progress."
            return
        }
        player.tell(jump.message)
        cutShort += index
        current = null
        index = jump.index
        player.saveStep(index)
    }

    companion object {
        const val DIED = "Autopilot: stopped, you died."
    }
}
