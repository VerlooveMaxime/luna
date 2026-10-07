package game.idle

import game.idle.flow.StepSettings
import game.idle.location.Tile
import io.luna.util.GsonUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class IdleStateTest {

    private val runTile = Tile(3200, 3200)
    private val chop = StepSettings("chop", mapOf("tree" to "normal"))
    private val state = IdleState(steps = listOf(chop), stepIndex = 1, running = true, runTile = runTile, stage = 3, resets = 2)

    @Test
    fun `new state has no flow and is not running`() {
        val fresh = IdleState()

        assertEquals(emptyList<StepSettings>(), fresh.steps)
        assertFalse(fresh.running)
        assertEquals(listOf(0, 0, 0), listOf(fresh.stepIndex, fresh.stage, fresh.resets))
    }

    @Test
    fun `a new flow starts at step zero, stopped, with no run tile`() {
        val drop = StepSettings("drop")

        val changed = state.withFlow(listOf(drop))

        assertEquals(IdleState(steps = listOf(drop), stepIndex = 0, running = false, stage = 3, resets = 2), changed)
    }

    @Test
    fun `moving to a step keeps everything else`() {
        assertEquals(state.copy(stepIndex = 4), state.atStep(4))
    }

    @Test
    fun `stopping only flips the switch`() {
        assertEquals(state.copy(running = false), state.stopped())
    }

    @Test
    fun `starting from the first step takes the player's tile as the run tile`() {
        val started = state.stopped().fromStart().started(Tile(1, 2))

        assertEquals(state.copy(stepIndex = 0, runTile = Tile(1, 2)), started)
    }

    @Test
    fun `a lap is counted, and a new run or a new flow counts from zero again`() {
        val lapped = state.lapped().lapped()

        assertEquals(listOf(2, 0, 0), listOf(lapped.laps, lapped.fromStart().laps, lapped.withFlow(emptyList()).laps))
    }

    @Test
    fun `resuming keeps the run tile it was started on`() {
        assertEquals(state, state.stopped().started(Tile(1, 2)))
    }

    @Test
    fun `the run tile is saved and read back`() {
        val json = GsonUtils.GSON.toJson(state)

        assertEquals(state, GsonUtils.GSON.fromJson(json, IdleState::class.java))
    }

    @Test
    fun `steps and saved flows are saved and read back`() {
        val saving = state.copy(savedFlows = listOf(SavedFlow(1, "Oaks", listOf(chop))), savedSlot = 1)

        assertEquals(saving, GsonUtils.GSON.fromJson(GsonUtils.GSON.toJson(saving), IdleState::class.java))
    }

    @Test
    fun `a save from before structured steps loads with an empty flow and keeps the rest`() {
        val saved = GsonUtils.GSON.fromJson(
            """{ "flow": ["chop oak", "drop"], "step_index": 1, "stage": 2, "tutorial_step": 5 }""",
            IdleState::class.java,
        )

        assertEquals(IdleState(stepIndex = 1, stage = 2, tutorialStep = 5), saved)
    }

    @Test
    fun `a new state has finished the tutorial, so only new characters are given one`() {
        assertEquals(IdleState.TUTORIAL_DONE, IdleState().tutorialStep)
    }

    @Test
    fun `a state saved before the tutorial existed loads as finished`() {
        val saved = GsonUtils.GSON.fromJson("""{ "flow": [], "step_index": 2, "running": false }""", IdleState::class.java)

        assertEquals(IdleState.TUTORIAL_DONE, saved.tutorialStep)
    }
}
