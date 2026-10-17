package game.idle.autopilot.mining

import game.idle.autopilot.travel.FakeTraveller
import game.idle.autopilot.travel.WalkUps
import game.idle.flow.Meter
import game.idle.flow.Reason
import game.idle.flow.StepOutcome
import game.idle.flow.Target
import game.idle.location.Tile
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiningWorkTest {

    private val near = rock(3201, 3200, distance = 2)
    private val other = rock(3205, 3200, distance = 5)
    private val inReach = near.copy(distance = 0, usableFromHere = true)
    private val anchor = Tile(3203, 3203)

    private val miner = FakeMiner(miningView(listOf(inReach)), anchor)
    /** Stands away from every target, so a walk up walks; its walks join the adapter's steps. */
    private val traveller = FakeTraveller(Position(3000, 3000), miner.steps)
    private val work = MiningWork(miner, WalkUps(traveller))

    @Test
    fun `the meter reads the ores carried`() {
        miner.ores = 4

        assertEquals(4, (work.meter as Meter.Rises).reading())
    }

    @Test
    fun `mining has nothing to settle before the step may end`() {
        assertFalse(work.finishing())
    }

    @Test
    fun `the work is busy while the miner is`() {
        miner.busy = true

        assertTrue(work.isBusy())
    }

    @Test
    fun `interrupting breaks off the mining`() {
        work.interrupt()

        assertEquals(listOf("interrupt"), miner.steps)
    }

    @Test
    fun `a rock in reach is mined, which is work`() {
        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Worked to listOf("mine 3201,3200"), outcome to miner.steps)
    }

    @Test
    fun `a rock out of reach is walked to, which approaches it`() {
        miner.view = miningView(listOf(near))

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Approached to listOf("walk to 3200,3200"), outcome to miner.steps)
    }

    @Test
    fun `away from the area with no rock in sight the step goes back to its anchor`() {
        miner.view = miningView(emptyList(), atLocation = false)

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Away(anchor) to emptyList<String>(), outcome to miner.steps)
    }

    @Test
    fun `inside the area with no rock in reach the step waits for one, clicking nothing`() {
        miner.view = miningView(emptyList())

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Waiting(Reason.NoneInReach(Target.ROCK)) to emptyList<String>(), outcome to miner.steps)
    }
}
