package game.idle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class IdleStateTest {

    private val state = IdleState(flow = listOf("chop normal @varrock_west"), stepIndex = 1, running = true, stage = 3, resets = 2)

    @Test
    fun `new state has no flow and is not running`() {
        val fresh = IdleState()

        assertEquals(emptyList<String>(), fresh.flow)
        assertFalse(fresh.running)
        assertEquals(listOf(0, 0, 0), listOf(fresh.stepIndex, fresh.stage, fresh.resets))
    }

    @Test
    fun `a new flow starts at step zero and stopped`() {
        val changed = state.withFlow(listOf("loop"))

        assertEquals(IdleState(flow = listOf("loop"), stepIndex = 0, running = false, stage = 3, resets = 2), changed)
    }

    @Test
    fun `moving to a step keeps everything else`() {
        assertEquals(state.copy(stepIndex = 4), state.atStep(4))
    }

    @Test
    fun `starting and stopping only flip the switch`() {
        assertEquals(state.copy(running = false), state.stopped())
        assertEquals(state, state.stopped().started())
    }
}
