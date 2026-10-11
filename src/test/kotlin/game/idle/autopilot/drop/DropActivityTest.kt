package game.idle.autopilot.drop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DropActivityTest {

    private class FakeItemDropper(var items: Int) : ItemDropper {
        var busy = false
        var drops = 0

        override fun isBusy(): Boolean = busy

        override fun hasItems(): Boolean = items > 0

        override fun dropItems() {
            drops++
            items = 0
        }
    }

    @Test
    fun `the step is busy while the dropper is`() {
        val dropper = FakeItemDropper(items = 3).apply { busy = true }

        assertTrue(DropActivity(dropper).isBusy())
    }

    @Test
    fun `the step is not done while items remain`() {
        assertFalse(DropActivity(FakeItemDropper(items = 3)).isDone())
    }

    @Test
    fun `acting drops the items and ends the step`() {
        val dropper = FakeItemDropper(items = 3)
        val activity = DropActivity(dropper)

        activity.act()

        assertEquals(1, dropper.drops)
        assertTrue(activity.isDone())
    }

    @Test
    fun `dropping never blocks, stops the flow or has anything to say`() {
        val activity = DropActivity(FakeItemDropper(items = 3))

        assertEquals(listOf(null, null, null), listOf(activity.blocked(), activity.stopping(), activity.said()))
    }
}
