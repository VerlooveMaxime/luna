package game.idle.autopilot.walk

import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WalkActivityTest {

    private val target = Tile(3100, 3100)
    private val walker = FakeWalker(Tile(3050, 3050))
    private val activity = WalkActivity(walker, target)
    private val noWay = "Autopilot: there is no way to walk to 3100 3100 from here."

    @Test
    fun `away from the target it walks`() {
        activity.act()

        assertEquals(1, walker.walks)
        assertFalse(activity.isDone())
    }

    @Test
    fun `busy follows the walker`() {
        walker.busy = true

        assertTrue(activity.isBusy())
    }

    @Test
    fun `on the target the step is over`() {
        walker.here = target

        activity.act()

        assertTrue(activity.isDone())
        assertEquals(0, walker.walks)
    }

    @Test
    fun `next to the target counts as arrived`() {
        walker.here = Tile(3101, 3099)

        activity.act()

        assertTrue(activity.isDone())
    }

    @Test
    fun `two tiles away is not arrived`() {
        walker.here = Tile(3102, 3100)

        activity.act()

        assertFalse(activity.isDone())
    }

    @Test
    fun `a walk that got closer but not there walks again`() {
        activity.act()
        walker.here = Tile(3080, 3080)

        activity.act()

        assertEquals(2, walker.walks)
        assertEquals(emptyList<String>(), walker.told)
    }

    @Test
    fun `a walk that ended where it started blocks once with a message`() {
        activity.act()
        activity.act()
        activity.act()

        assertEquals(1, walker.walks)
        assertEquals(listOf(noWay), walker.told)
        assertFalse(activity.isDone())
    }

    @Test
    fun `a blocked walk tries again once the player stands elsewhere, and can block again`() {
        activity.act()
        activity.act()
        walker.here = Tile(3060, 3060)

        activity.act()
        activity.act()

        assertEquals(2, walker.walks)
        assertEquals(listOf(noWay, noWay), walker.told)
    }

    @Test
    fun `a target on another floor blocks once`() {
        walker.here = Tile(3100, 3100, 1)

        activity.act()
        activity.act()

        assertEquals(0, walker.walks)
        assertEquals(listOf("Autopilot: the walk step cannot change floors to reach 3100 3100."), walker.told)
    }
}
