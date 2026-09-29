package game.idle.autopilot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AutopilotDriverTest {

    private val activity = FakeActivity()
    private val driver = AutopilotDriver(activity, decisionDelayTicks = 2)

    @Test
    fun `nothing happens before the player has been idle for the delay`() {
        driver.tick()

        assertEquals(0, activity.steps)
    }

    @Test
    fun `the activity takes a step once the player has been idle for the delay`() {
        driver.tick()
        driver.tick()

        assertEquals(1, activity.steps)
    }

    @Test
    fun `the wait starts over after each step`() {
        driver.tick()
        driver.tick()
        driver.tick()

        assertEquals(1, activity.steps)
    }

    @Test
    fun `a busy tick restarts the wait`() {
        driver.tick()
        activity.busy = true
        driver.tick()
        activity.busy = false
        driver.tick()

        assertEquals(0, activity.steps)
    }

    @Test
    fun `a busy player is left alone`() {
        activity.busy = true

        driver.tick()
        driver.tick()

        assertEquals(0, activity.steps)
    }

    @Test
    fun `the delay must be positive`() {
        assertThrows<IllegalArgumentException> { AutopilotDriver(activity, decisionDelayTicks = 0) }
    }
}
