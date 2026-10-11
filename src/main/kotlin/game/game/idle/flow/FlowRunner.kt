package game.idle.flow

import game.idle.autopilot.AutopilotActivity

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
 * again on its next turn before it made progress stops the flow, as does the player's death. A stop while something
 * attacks the player waits: they run until nothing does, then the flow stops (Maxime, 2026-10-10). The step index is saved
 * whenever it changes so a relog resumes where it was. What the steps report is put into [words] here, the one place
 * that knows their numbers.
 */
class FlowRunner(
    private val flow: ResolvedFlow,
    startIndex: Int,
    private val player: FlowPlayer,
    private val words: StepWords,
) : AutopilotActivity {

    private val steps = flow.steps
    private var index = if (steps.isEmpty()) 0 else startIndex.coerceIn(0, steps.lastIndex)
    private var current: ReflexGuard? = null
    private var stopReason: String? = null

    /** The steps a jump cut short on their last turn. */
    private val cutShort = mutableSetOf<Int>()

    /** Why the flow stops, once nothing attacks the player. */
    private var stopped: String? = null

    override fun isBusy(): Boolean = stopping() == null && current?.isBusy() ?: false

    override fun stopReason(): String? = if (player.body.dead()) StepWords.DIED else stopped

    /** The current step's block; none between two steps. */
    override fun blocked(): String? = current?.blocked()?.let(words::block)

    override fun act() {
        if (steps.isEmpty()) return
        if (stopping() == null) step()
        stopping()?.let(::leave)
    }

    private fun step() {
        val activity = current ?: startStep()
        if (activity.isDone()) {
            advance()
            return
        }
        activity.act()
        activity.said()?.let { player.tell(words.said(index + 1, it)) }
        activity.jump()?.let { jump(activity, it) }
    }

    /** Why the flow or its step wants to stop, held while the player runs from what attacks them. */
    private fun stopping(): String? = stopReason ?: current?.stopping()?.let { words.stop(index + 1, it) }

    private fun leave(reason: String) {
        if (player.body.underAttack()) {
            player.body.flee()
            return
        }
        player.body.calm()
        stopped = reason
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
            stopReason = words.cutShort(index + 1)
            return
        }
        player.tell(words.jump(jump.number, jump.health, jump.index + 1))
        cutShort += index
        current = null
        index = jump.index
        player.saveStep(index)
    }
}
