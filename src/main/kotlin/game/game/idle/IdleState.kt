package game.idle

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import game.idle.flow.StepSettings
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/**
 * Per-player IdleRS progress. Persisted as one player attribute, so every field must stay Gson-friendly: the flow is
 * kept as its [steps]' settings and resolved again when it runs. [runTile] is where the flow was started from its first
 * step, the work spot of action steps no walk step comes before; [laps] counts the times it went round since then (the
 * tutorial's lessons wait for them). [savedFlows] are the flows the player keeps to switch between, and [savedSlot]
 * the one the current flow was last loaded from or saved to. [tutorialStep] is the value of a `TutorialStep`; a save
 * from before the tutorial existed loads as finished.
 *
 * The flow was kept as typed lines under the name `flow` until 2026-10-07; a save still holding them loads with an
 * empty flow, which the next save makes final.
 */
data class IdleState(
    val steps: List<StepSettings> = emptyList(),
    val stepIndex: Int = 0,
    val running: Boolean = false,
    val runTile: Tile? = null,
    val laps: Int = 0,
    val stage: Int = 0,
    val resets: Int = 0,
    val tutorialStep: Int = TUTORIAL_DONE,
    val savedFlows: List<SavedFlow> = emptyList(),
    val savedSlot: Int? = null,
) {
    fun withFlow(steps: List<StepSettings>): IdleState =
        copy(steps = steps, stepIndex = 0, running = false, runTile = null, laps = 0)

    fun atStep(index: Int): IdleState = copy(stepIndex = index)

    /** Back to the first step, to be started from wherever the player then stands. */
    fun fromStart(): IdleState = copy(stepIndex = 0, runTile = null, laps = 0)

    fun lapped(): IdleState = copy(laps = laps + 1)

    /** Running, from [here] unless it was started before and is resuming. */
    fun started(here: Tile): IdleState = copy(running = true, runTile = runTile ?: here)

    fun stopped(): IdleState = copy(running = false)

    companion object {
        const val TUTORIAL_DONE = 1000
    }
}

/** A flow the player saved under [name] in saved-flow slot [slot]. */
data class SavedFlow(val slot: Int = 0, val name: String = "", val steps: List<StepSettings> = emptyList())

var Player.idleState by Attr.obj { IdleState() }.persist("idle_state")
