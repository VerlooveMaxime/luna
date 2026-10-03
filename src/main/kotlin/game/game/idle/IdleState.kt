package game.idle

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/**
 * Per-player IdleRS progress. Persisted as one player attribute, so every field must stay Gson-friendly: the flow
 * is kept as the lines the player typed and parsed again when it runs. [runTile] is where the flow was started from
 * its first step, the work spot of action steps no walk step comes before. [tutorialStep] is the value of a
 * `TutorialStep`; a save from before the tutorial existed loads as finished.
 */
data class IdleState(
    val flow: List<String> = emptyList(),
    val stepIndex: Int = 0,
    val running: Boolean = false,
    val runTile: Tile? = null,
    val stage: Int = 0,
    val resets: Int = 0,
    val tutorialStep: Int = TUTORIAL_DONE,
) {
    fun withFlow(flow: List<String>): IdleState = copy(flow = flow, stepIndex = 0, running = false, runTile = null)

    fun atStep(index: Int): IdleState = copy(stepIndex = index)

    /** Back to the first step, to be started from wherever the player then stands. */
    fun fromStart(): IdleState = copy(stepIndex = 0, runTile = null)

    /** Running, from [here] unless it was started before and is resuming. */
    fun started(here: Tile): IdleState = copy(running = true, runTile = runTile ?: here)

    fun stopped(): IdleState = copy(running = false)

    companion object {
        const val TUTORIAL_DONE = 1000
    }
}

var Player.idleState by Attr.obj { IdleState() }.persist("idle_state")
