package game.idle.autopilot.rest

import game.idle.flow.Health
import game.idle.flow.Meter
import game.idle.flow.StepOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class RestWorkTest {

    private var health = Health(10, 20)

    private fun resting(percent: Int = 100) = RestWork({ health }, percent)

    @Test
    fun `below the percent each decision rests, which is the work`() {
        assertEquals(StepOutcome.Worked, resting().act(null))
    }

    @Test
    fun `hitpoints at the percent, rounded up, finish the rest`() {
        health = Health(18, 25)

        assertEquals(StepOutcome.Finished, resting(percent = 70).act(null))
    }

    @Test
    fun `one hitpoint short of the rounded up percent rests on`() {
        health = Health(17, 25)

        assertEquals(StepOutcome.Worked, resting(percent = 70).act(null))
    }

    @Test
    fun `hitpoints already there finish the rest at once`() {
        health = Health(20, 20)

        assertEquals(StepOutcome.Finished, resting().act(null))
    }

    @Test
    fun `resting is never busy`() {
        assertFalse(resting().isBusy())
    }

    @Test
    fun `resting has no unit settling`() {
        assertFalse(resting().finishing())
    }

    @Test
    fun `the count is the hitpoints`() {
        health = Health(13, 20)

        assertEquals(13, (resting().meter as Meter.Rises).reading())
    }

    @Test
    fun `there is nothing to break off`() {
        resting().interrupt()

        assertEquals(Health(10, 20), health)
    }
}
