package game.idle.autopilot.walk

import game.idle.flow.Place
import game.idle.flow.Reason
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WalkActivityTest {

    private val target = Tile(3100, 3100)
    private val walker = FakeWalker(Tile(3050, 3050))
    private val activity = WalkActivity(walker, target, Place.TILE)
    private val noWay = Reason.NoWay(Place.TILE)

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
    }

    @Test
    fun `a walk that ended where it started stops walking`() {
        activity.act()
        activity.act()
        activity.act()

        assertEquals(1, walker.walks)
        assertFalse(activity.isDone())
    }

    @Test
    fun `a block is reported with its reason`() {
        activity.act()

        activity.act()

        assertEquals(noWay, activity.blocked())
    }

    @Test
    fun `a block clears once the walk goes on`() {
        activity.act()
        activity.act()
        walker.here = Tile(3060, 3060)

        activity.act()

        assertNull(activity.blocked())
    }

    @Test
    fun `a blocked walk tries again once the player stands elsewhere, and can block again`() {
        activity.act()
        activity.act()
        walker.here = Tile(3060, 3060)

        activity.act()
        activity.act()

        assertEquals(listOf<Any?>(2, noWay), listOf(walker.walks, activity.blocked()))
    }

    @Test
    fun `a target on another floor blocks without walking`() {
        walker.here = Tile(3100, 3100, 1)

        activity.act()

        assertEquals(listOf<Any?>(0, Reason.OtherFloor(Place.TILE)), listOf(walker.walks, activity.blocked()))
    }

    @Test
    fun `a walk to a step's work spot blocks on the spot when there is no way to it`() {
        val toSpot = WalkActivity(walker, target, Place.WORK_SPOT)
        toSpot.act()

        toSpot.act()

        assertEquals(Reason.NoWay(Place.WORK_SPOT), toSpot.blocked())
    }

    @Test
    fun `a walk never stops the flow and has nothing to say`() {
        assertEquals(listOf(null, null), listOf(activity.stopping(), activity.said()))
    }
}
