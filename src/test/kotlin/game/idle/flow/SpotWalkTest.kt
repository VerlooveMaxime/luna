package game.idle.flow

import game.idle.location.Area
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpotWalkTest {

    private val log = mutableListOf<String>()
    private val walk = FakeStepActivity("walk", log)
    private val work = FakeStepActivity("work", log)
    private val built = mutableListOf<String>()
    private val spotWalk = SpotWalk(walk) { work.also { built += "work" } }
    private val area = Area(Tile(3101, 3509), 10)

    /** What [SpotWalk.of] walks to, and what it gives for the player on [here]. */
    private fun startedOn(here: Tile, area: Area?): Pair<List<Tile>, StepActivity> {
        val walkedTo = mutableListOf<Tile>()
        val activity = SpotWalk.of(here, area, walk = { walkedTo += it; walk }) { work }
        return walkedTo to activity
    }

    private fun arrive() {
        walk.done = true
        spotWalk.act()
    }

    @Test
    fun `a step working where the flow went works at once`() {
        assertEquals(emptyList<Tile>() to work, startedOn(Tile(3000, 3000), area = null))
    }

    @Test
    fun `a step starting inside its own spot's area works at once`() {
        assertEquals(emptyList<Tile>() to work, startedOn(Tile(3110, 3500), area))
    }

    @Test
    fun `a step starting outside its own spot's area walks to the spot first`() {
        val (walkedTo, activity) = startedOn(Tile(3112, 3509), area)

        assertEquals(listOf<Any>(listOf(Tile(3101, 3509)), SpotWalk::class), listOf(walkedTo, activity::class))
    }

    @Test
    fun `while it walks only the walk acts and the work is not built yet`() {
        spotWalk.act()

        assertEquals(listOf<Any>(listOf("walk:1"), emptyList<String>()), listOf(log, built))
    }

    @Test
    fun `once there the work is built and acts in the same decision`() {
        arrive()

        assertEquals(listOf<Any>(listOf("walk:1", "work:1"), listOf("work")), listOf(log, built))
    }

    @Test
    fun `after the walk every decision is the work's`() {
        arrive()

        spotWalk.act()

        assertEquals(listOf("walk:1", "work:1", "work:2"), log)
    }

    @Test
    fun `while it walks it is busy as the walk is`() {
        walk.busy = true

        assertTrue(spotWalk.isBusy())
    }

    @Test
    fun `after the walk it is busy as the work is`() {
        arrive()
        walk.busy = true

        assertFalse(spotWalk.isBusy())
    }

    @Test
    fun `a walk that cannot get there blocks with its reason`() {
        walk.blocked = Reason.NoWay(Place.WORK_SPOT)

        assertEquals(Reason.NoWay(Place.WORK_SPOT), spotWalk.blocked())
    }

    @Test
    fun `after the walk the block is the work's`() {
        arrive()
        work.blocked = Reason.NoneInReach(Target.TREE)

        assertEquals(Reason.NoneInReach(Target.TREE), spotWalk.blocked())
    }

    @Test
    fun `a walk never speaks`() {
        walk.said = Said.Ended(Reason.BagFull(emptyList()))

        assertNull(spotWalk.said())
    }

    @Test
    fun `after the walk what the work says is the step's`() {
        arrive()
        work.said = Said.Ended(Reason.BagFull(emptyList()))

        assertEquals(Said.Ended(Reason.BagFull(emptyList())), spotWalk.said())
    }

    @Test
    fun `the step is not over while it walks`() {
        walk.done = true

        assertFalse(spotWalk.isDone())
    }

    @Test
    fun `the step is over when its work is`() {
        arrive()
        work.done = true

        assertTrue(spotWalk.isDone())
    }

    @Test
    fun `a walk never stops the flow`() {
        walk.stop = StepStop.Stuck(Reason.BankFull)

        assertNull(spotWalk.stopping())
    }

    @Test
    fun `after the walk the work may stop the flow`() {
        arrive()
        work.stop = StepStop.Stuck(Reason.BankFull)

        assertEquals(StepStop.Stuck(Reason.BankFull), spotWalk.stopping())
    }

    @Test
    fun `a walk counts nothing done`() {
        work.amountDone = 3

        assertEquals(0, spotWalk.amountDone())
    }

    @Test
    fun `after the walk the work's count is the step's`() {
        arrive()
        work.amountDone = 3

        assertEquals(3, spotWalk.amountDone())
    }

    @Test
    fun `a walk never waits for the world`() {
        walk.waiting = Reason.NoneInReach(Target.TREE)

        assertNull(spotWalk.waiting())
    }

    @Test
    fun `after the walk what the work waits for is the step's`() {
        arrive()
        work.waiting = Reason.NoneInReach(Target.TREE)

        assertEquals(Reason.NoneInReach(Target.TREE), spotWalk.waiting())
    }

    @Test
    fun `a walk is no work`() {
        walk.worked = true

        assertFalse(spotWalk.worked())
    }

    @Test
    fun `after the walk whether the work worked is the step's`() {
        arrive()
        work.worked = false

        assertFalse(spotWalk.worked())
    }

    @Test
    fun `a turn ended while it walks ends the walk and is over without the work`() {
        spotWalk.endTurn()

        assertEquals(listOf<Any>(1, true, emptyList<String>()), listOf(walk.ended, spotWalk.isDone(), built))
    }

    @Test
    fun `a turn ended after the walk ends the work`() {
        arrive()

        spotWalk.endTurn()

        assertEquals(1 to 0, work.ended to walk.ended)
    }
}
