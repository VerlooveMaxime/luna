package game.idle

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import io.luna.game.model.mob.Player

/**
 * Per-player IdleRS progress. Persisted as one player attribute, so every field must stay Gson-friendly: the flow
 * is kept as the lines the player typed and parsed again when it runs. [tutorialStep] is the value of a
 * `TutorialStep`; a save from before the tutorial existed loads as finished.
 */
data class IdleState(
    val flow: List<String> = emptyList(),
    val stepIndex: Int = 0,
    val running: Boolean = false,
    val stage: Int = 0,
    val resets: Int = 0,
    val tutorialStep: Int = TUTORIAL_DONE,
) {
    fun withFlow(flow: List<String>): IdleState = copy(flow = flow, stepIndex = 0, running = false)

    fun atStep(index: Int): IdleState = copy(stepIndex = index)

    fun started(): IdleState = copy(running = true)

    fun stopped(): IdleState = copy(running = false)

    companion object {
        const val TUTORIAL_DONE = 1000
    }
}

var Player.idleState by Attr.obj { IdleState() }.persist("idle_state")
