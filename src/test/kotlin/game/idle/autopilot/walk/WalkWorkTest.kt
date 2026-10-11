package game.idle.autopilot.walk

import game.idle.flow.Meter
import game.idle.flow.Place
import game.idle.flow.Reason
import game.idle.flow.StepOutcome
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WalkWorkTest {

    private val target = Tile(3100, 3100)
    private val walker = FakeWalker(Tile(3050, 3050))
    private val work = WalkWork(walker, target, Place.TILE)
    private val noWay = StepOutcome.Blocked(Reason.NoWay(Place.TILE))

    @Test
    fun `away from the target it walks and approaches`() {
        val outcome = work.act(null)

        assertEquals(1 to StepOutcome.Approached, walker.walks to outcome)
    }

    @Test
    fun `busy follows the walker`() {
        walker.busy = true

        assertTrue(work.isBusy())
    }

    @Test
    fun `on the target the walk is finished without walking`() {
        walker.here = target

        val outcome = work.act(null)

        assertEquals(StepOutcome.Finished to 0, outcome to walker.walks)
    }

    @Test
    fun `next to the target counts as arrived`() {
        walker.here = Tile(3101, 3099)

        assertEquals(StepOutcome.Finished, work.act(null))
    }

    @Test
    fun `two tiles away is not arrived`() {
        walker.here = Tile(3102, 3100)

        assertEquals(StepOutcome.Approached, work.act(null))
    }

    @Test
    fun `a walk that got closer but not there walks again`() {
        work.act(null)
        walker.here = Tile(3080, 3080)

        work.act(null)

        assertEquals(2, walker.walks)
    }

    @Test
    fun `a walk that ended where it started blocks instead of walking again`() {
        work.act(null)

        val outcome = work.act(null)

        assertEquals(noWay to 1, outcome to walker.walks)
    }

    @Test
    fun `a blocked walk goes on once the player stands elsewhere`() {
        work.act(null)
        work.act(null)
        walker.here = Tile(3060, 3060)

        assertEquals(StepOutcome.Approached, work.act(null))
    }

    @Test
    fun `a walk that went on can block again`() {
        work.act(null)
        work.act(null)
        walker.here = Tile(3060, 3060)
        work.act(null)

        assertEquals(noWay, work.act(null))
    }

    @Test
    fun `a target on another floor blocks without walking`() {
        walker.here = Tile(3100, 3100, 1)

        val outcome = work.act(null)

        assertEquals(StepOutcome.Blocked(Reason.OtherFloor(Place.TILE)) to 0, outcome to walker.walks)
    }

    @Test
    fun `a walk to a step's work spot blocks on the spot`() {
        val toSpot = WalkWork(walker, target, Place.WORK_SPOT)
        toSpot.act(null)

        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.WORK_SPOT)), toSpot.act(null))
    }

    @Test
    fun `a walk counts nothing and never has a unit settling`() {
        assertEquals(Meter.None to false, work.meter to work.finishing())
    }

    @Test
    fun `interrupting breaks off the walker's action`() {
        work.interrupt()

        assertEquals(1, walker.interrupted)
    }

    @Test
    fun `an idle walker leaves the walk free to act`() {
        assertFalse(work.isBusy())
    }
}
