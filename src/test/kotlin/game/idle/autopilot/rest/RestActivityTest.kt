package game.idle.autopilot.rest

import game.idle.flow.Health
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RestActivityTest {

    private var health = Health(10, 20)

    private fun resting(percent: Int = 100) = RestActivity({ health }, percent)

    @Test
    fun `resting is not done below the percent`() {
        assertFalse(resting().isDone())
    }

    @Test
    fun `resting is done once hitpoints reach the percent, rounded up`() {
        health = Health(18, 25)

        assertTrue(resting(percent = 70).isDone())
    }

    @Test
    fun `resting is not done one hitpoint short of the rounded up percent`() {
        health = Health(17, 25)

        assertFalse(resting(percent = 70).isDone())
    }

    @Test
    fun `resting ends at once when hitpoints are already there`() {
        health = Health(20, 20)

        assertTrue(resting().isDone())
    }

    @Test
    fun `resting is never busy and does nothing when it acts`() {
        val activity = resting()

        activity.act()

        assertEquals(listOf(false, Health(10, 20)), listOf(activity.isBusy(), health))
    }

    @Test
    fun `the hitpoints won back count as progress`() {
        val activity = resting()
        health = Health(13, 20)

        assertEquals(3, activity.amountDone())
    }

    @Test
    fun `hitpoints lost while resting count as no progress`() {
        val activity = resting()
        health = Health(7, 20)

        assertEquals(0, activity.amountDone())
    }

    @Test
    fun `resting never blocks, stops the flow or has anything to say`() {
        assertEquals(listOf(null, null, null), listOf(resting().blocked(), resting().stopping(), resting().said()))
    }
}
