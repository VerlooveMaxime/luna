package game.idle

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import game.idle.flow.ReflexSettings
import game.idle.flow.RunProgress
import game.idle.flow.StepSettings
import game.idle.flow.StopSettings
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
 * step. [progress] is what the run did so far (S12: per-step totals, met goals, when it started), saved so a relog
 * resumes it, kept after a stop (the slots keep saying which goals were met) and cleared when a run starts afresh or
 * the flow changes; [stop] is the flow's stop condition, saved with the flow.
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
    val progress: RunProgress = RunProgress(),
    val stop: StopSettings = StopSettings(),
    @Transient val blocked: String? = null,
    @Transient val waiting: String? = null,
) {
    /**
     * The flow as [steps] and [reflexes], the reflexes and the [stop] kept as they are unless given, stopped and from its
     * first step.
     */
    fun withFlow(steps: List<StepSettings>, reflexes: List<ReflexSettings> = this.reflexes, stop: StopSettings = this.stop): IdleState =
        copy(steps = steps, reflexes = reflexes, stepIndex = 0, running = false, runTile = null, laps = 0, progress = RunProgress(), stop = stop, blocked = null, waiting = null)

    fun atStep(index: Int): IdleState = copy(stepIndex = index, blocked = null, waiting = null)

    /** Back to the first step, to be started from wherever the player then stands, nothing done yet. */
    fun fromStart(): IdleState = copy(stepIndex = 0, runTile = null, laps = 0, progress = RunProgress())

    fun lapped(): IdleState = copy(laps = laps + 1)

    /** Running, from [here] and [now] unless it was started before and is resuming. */
    fun started(here: Tile, now: Long): IdleState =
        copy(running = true, runTile = runTile ?: here, progress = if (runTile == null) progress.copy(startedAt = now) else progress, blocked = null, waiting = null)

    fun withProgress(progress: RunProgress): IdleState = copy(progress = progress)

    fun stopped(): IdleState = copy(running = false, blocked = null, waiting = null)

    companion object {
        const val TUTORIAL_DONE = 1000
    }
}

/** A flow the player saved under [name] in saved-flow slot [slot], its stop condition with it (S12). */
data class SavedFlow(
    val slot: Int = 0,
    val name: String = "",
    val steps: List<StepSettings> = emptyList(),
    val reflexes: List<ReflexSettings> = emptyList(),
    val stop: StopSettings = StopSettings(),
)

var Player.idleState by Attr.obj { IdleState() }.persist("idle_state")
