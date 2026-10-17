package game.idle.autopilot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

class AutopilotDriverTest {

    private val activity = FakeActivity()
    private val driver = AutopilotDriver(activity) { 2 }

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
    fun `the activity's block is the driver's`() {
        activity.blocked = "Needs an axe you can use"

        assertEquals("Needs an axe you can use", driver.blocked())
    }

    @Test
    fun `what the activity waits for is the driver's`() {
        activity.waiting = "No tree in reach"

        assertEquals("No tree in reach", driver.waiting())
    }

    @Test
    fun `what the activity does on its way is the driver's`() {
        activity.underWay = "Opening the door"

        assertEquals("Opening the door", driver.underWay())
    }

    @Test
    fun `ending the driver ends its activity`() {
        driver.end()

        assertEquals(1, activity.ended)
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
    fun `each step waits as long as the delay draws for it`() {
        val waits = ArrayDeque(listOf(1, 3))
        val drawing = AutopilotDriver(activity) { waits.removeFirst() }
        drawing.tick()
        drawing.tick()
        drawing.tick()

        assertEquals(1, activity.steps)
    }

    @Test
    fun `a delay drawn as nothing is refused`() {
        assertThrows<IllegalArgumentException> { AutopilotDriver(activity) { 0 } }
    }

    @Test
    fun `a delay between two lengths draws every length in between, and no other`() {
        val delay = DecisionDelay.between(1..3, Random(7))

        assertEquals(setOf(1, 2, 3), List(60) { delay.nextTicks() }.toSet())
    }
}
