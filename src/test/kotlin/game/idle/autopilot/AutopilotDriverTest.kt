package game.idle.autopilot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AutopilotDriverTest {

    private val activity = FakeActivity()
    private val driver = AutopilotDriver(activity, decisionDelayTicks = 2)

    @Test
    fun `the activity's reason to stop is the driver's`() {
        activity.stop = "out of food"

        assertEquals("out of food", driver.stopReason())
    }

    @Test
    fun `an activity with no reason to stop leaves the driver going`() {
        assertEquals(null, driver.stopReason())
    }

    @Test
    fun `an activity never stops the autopilot unless it says why`() {
        val plain = object : AutopilotActivity {
            override fun isBusy() = false
            override fun act() = Unit
        }

        assertEquals(null, plain.stopReason())
    }

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
