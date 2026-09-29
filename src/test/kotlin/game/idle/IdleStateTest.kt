package game.idle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdleStateTest {

    private val job = AutopilotJob("varrock_west", listOf("normal"))

    @Test
    fun `new state starts with autopilot off at stage zero`() {
        val state = IdleState()

        assertFalse(state.autopilotEnabled)
        assertEquals(0, state.stage)
        assertEquals(0, state.resets)
    }

    @Test
    fun `a saved job means the autopilot is on`() {
        assertTrue(IdleState(job = job).autopilotEnabled)
    }

    @Test
    fun `starting a job keeps stage and resets`() {
        val state = IdleState(job = null, stage = 3, resets = 2)

        assertEquals(IdleState(job = job, stage = 3, resets = 2), state.withJob(job))
    }

    @Test
    fun `stopping keeps stage and resets`() {
        val state = IdleState(job = job, stage = 3, resets = 2)

        assertEquals(IdleState(job = null, stage = 3, resets = 2), state.stopped())
    }
}
