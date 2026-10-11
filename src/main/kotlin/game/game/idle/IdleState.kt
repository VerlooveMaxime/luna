package game.idle

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import game.idle.flow.ReflexSettings
import game.idle.flow.StepSettings
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/**
 * Per-player IdleRS progress. Persisted as one player attribute, so every field must stay Gson-friendly: the flow is
 * kept as its [steps]' settings and its [reflexes]' (S07c), resolved again when it runs. [runTile] is where the flow was started from its first
 * step, the work spot of action steps no walk step comes before; [laps] counts the times it went round since then (the
 * tutorial's lessons wait for them). [savedFlows] are the flows the player keeps to switch between, and [savedSlot]
 * the one the current flow was last loaded from or saved to. [tutorialStep] is the value of a `TutorialStep`; a save
 * from before the tutorial existed loads as finished. [countBoostedLevels] is the player's choice to have the builder's
 * options judged on boosted levels instead of the unboosted ones; a flow that stops when a boost wears off is theirs.
 * [blocked] is why the running step cannot go on for now, a fragment such as "Needs an axe you can use" (`StepWords`),
 * shown in red on the overlay and the builder, and [waiting] what it waits for the world to supply ("No tree in reach"),
 * shown in yellow; both transient, so a save never holds them, and cleared whenever the flow starts, stops or changes
 * step.
 *
 * The flow was kept as typed lines under the name `flow` until 2026-10-07; a save still holding them loads with an
 * empty flow, which the next save makes final.
 */
data class IdleState(
    val steps: List<StepSettings> = emptyList(),
    val reflexes: List<ReflexSettings> = emptyList(),
    val stepIndex: Int = 0,
    val running: Boolean = false,
    val runTile: Tile? = null,
    val laps: Int = 0,
    val stage: Int = 0,
    val resets: Int = 0,
    val tutorialStep: Int = TUTORIAL_DONE,
    val savedFlows: List<SavedFlow> = emptyList(),
    val savedSlot: Int? = null,
    val countBoostedLevels: Boolean = false,
    @Transient val blocked: String? = null,
    @Transient val waiting: String? = null,
) {
    /** The flow as [steps] and [reflexes], the reflexes kept as they are unless given, stopped and from its first step. */
    fun withFlow(steps: List<StepSettings>, reflexes: List<ReflexSettings> = this.reflexes): IdleState =
        copy(steps = steps, reflexes = reflexes, stepIndex = 0, running = false, runTile = null, laps = 0, blocked = null, waiting = null)

    fun atStep(index: Int): IdleState = copy(stepIndex = index, blocked = null, waiting = null)

    /** Back to the first step, to be started from wherever the player then stands. */
    fun fromStart(): IdleState = copy(stepIndex = 0, runTile = null, laps = 0)

    fun lapped(): IdleState = copy(laps = laps + 1)

    /** Running, from [here] unless it was started before and is resuming. */
    fun started(here: Tile): IdleState = copy(running = true, runTile = runTile ?: here, blocked = null, waiting = null)

    fun stopped(): IdleState = copy(running = false, blocked = null, waiting = null)

    companion object {
        const val TUTORIAL_DONE = 1000
    }
}

/** A flow the player saved under [name] in saved-flow slot [slot]. */
data class SavedFlow(
    val slot: Int = 0,
    val name: String = "",
    val steps: List<StepSettings> = emptyList(),
    val reflexes: List<ReflexSettings> = emptyList(),
)

var Player.idleState by Attr.obj { IdleState() }.persist("idle_state")
