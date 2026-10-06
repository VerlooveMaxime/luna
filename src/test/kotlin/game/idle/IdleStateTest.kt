package game.idle

import game.idle.location.Tile
import io.luna.util.GsonUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class IdleStateTest {

    private val runTile = Tile(3200, 3200)
    private val state = IdleState(flow = listOf("chop normal"), stepIndex = 1, running = true, runTile = runTile, stage = 3, resets = 2)

    @Test
    fun `new state has no flow and is not running`() {
        val fresh = IdleState()

        assertEquals(emptyList<String>(), fresh.flow)
        assertFalse(fresh.running)
        assertEquals(listOf(0, 0, 0), listOf(fresh.stepIndex, fresh.stage, fresh.resets))
    }

    @Test
    fun `a new flow starts at step zero, stopped, with no run tile`() {
        val changed = state.withFlow(listOf("loop"))

        assertEquals(IdleState(flow = listOf("loop"), stepIndex = 0, running = false, stage = 3, resets = 2), changed)
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
    fun `a new state has finished the tutorial, so only new characters are given one`() {
        assertEquals(IdleState.TUTORIAL_DONE, IdleState().tutorialStep)
    }

    @Test
    fun `a state saved before the tutorial existed loads as finished`() {
        val saved = GsonUtils.GSON.fromJson("""{ "flow": [], "step_index": 2, "running": false }""", IdleState::class.java)

        assertEquals(IdleState.TUTORIAL_DONE, saved.tutorialStep)
    }
}
