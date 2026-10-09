package game.idle.autopilot.firemaking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LightActivityTest {

    private val ready = LightView(hasTinderbox = true, logs = 3, lightable = 4, tileFree = true)
    private val lighter = FakeLighter(ready)
    private val activity = LightActivity(lighter)

    @Test
    fun `a log is lit where the player stands`() {
        activity.act()

        assertEquals(listOf("light 4"), lighter.steps)
        assertFalse(activity.isDone())
    }

    @Test
    fun `without an amount the step ends once no log is left`() {
        activity.act()
        lighter.view = ready.copy(logs = 0, lightable = null)

        activity.act()

        assertTrue(activity.isDone())
    }

    @Test
    fun `with no logs from the start the step waits and says so once`() {
        lighter.view = ready.copy(logs = 0, lightable = null)

        activity.act()
        activity.act()

        assertFalse(activity.isDone())
        assertEquals(LightBlockedReason.NO_LOGS.message, activity.blocked())
    }

    @Test
    fun `with an amount the step ends once that many logs were used`() {
        val counting = LightActivity(lighter, amount = 2)
        counting.act()
        lighter.view = ready.copy(logs = 1)

        counting.act()

        assertTrue(counting.isDone())
        assertEquals(listOf("light 4"), lighter.steps)
    }

    @Test
    fun `with an amount the step keeps lighting until it is reached`() {
        val counting = LightActivity(lighter, amount = 2)
        counting.act()
        lighter.view = ready.copy(logs = 2)

        counting.act()

        assertFalse(counting.isDone())
        assertEquals(listOf("light 4", "light 4"), lighter.steps)
    }

    @Test
    fun `a taken tile is stepped off first`() {
        lighter.view = ready.copy(tileFree = false)

        activity.act()

        assertEquals(listOf("step aside"), lighter.steps)
    }

    @Test
    fun `nowhere to step aside blocks the step`() {
        lighter.view = ready.copy(tileFree = false)
        lighter.aside = false

        activity.act()

        assertEquals(LightBlockedReason.NO_ROOM.message, activity.blocked())
    }

    @Test
    fun `a block is reported with its reason`() {
        lighter.view = ready.copy(hasTinderbox = false)

        activity.act()

        assertEquals(LightBlockedReason.NO_TINDERBOX.message, activity.blocked())
    }

    @Test
    fun `nowhere to step reports a block too`() {
        lighter.view = ready.copy(tileFree = false)
        lighter.aside = false

        activity.act()

        assertEquals(LightBlockedReason.NO_ROOM.message, activity.blocked())
    }

    @Test
    fun `a block clears on the next decision that is not one`() {
        lighter.view = ready.copy(hasTinderbox = false)
        activity.act()
        lighter.view = ready

        activity.act()

        assertNull(activity.blocked())
    }

    @Test
    fun `without a tinderbox the step blocks once with a message`() {
        lighter.view = ready.copy(hasTinderbox = false)

        activity.act()
        activity.act()

        assertEquals(LightBlockedReason.NO_TINDERBOX.message, activity.blocked())
        assertFalse(activity.isDone())
    }

    @Test
    fun `logs too hard to light block the step`() {
        lighter.view = ready.copy(lightable = null)

        activity.act()

        assertEquals(LightBlockedReason.LEVEL_TOO_LOW.message, activity.blocked())
    }

    @Test
    fun `the step is busy while the lighter is, so a fire is never left half lit`() {
        lighter.busy = true

        assertTrue(activity.isBusy())
    }
}
