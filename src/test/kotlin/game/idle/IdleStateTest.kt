package game.idle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdleStateTest {

    @Test
    fun `new state starts with autopilot off at stage zero`() {
        val state = IdleState()

        assertFalse(state.autopilotEnabled)
        assertEquals(0, state.stage)
        assertEquals(0, state.resets)
    }

    @Test
    fun `enabling autopilot keeps stage and resets`() {
        val state = IdleState(autopilotEnabled = false, stage = 3, resets = 2)

        val enabled = state.withAutopilot(enabled = true)

        assertTrue(enabled.autopilotEnabled)
        assertEquals(IdleState(autopilotEnabled = true, stage = 3, resets = 2), enabled)
    }

    @Test
    fun `disabling autopilot keeps stage and resets`() {
        val state = IdleState(autopilotEnabled = true, stage = 3, resets = 2)

        val disabled = state.withAutopilot(enabled = false)

        assertEquals(IdleState(autopilotEnabled = false, stage = 3, resets = 2), disabled)
    }
}
