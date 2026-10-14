package game.idle.autopilot.fishing

import game.idle.autopilot.Candidate
import game.idle.autopilot.fishing.FishingDecision.WalkTo
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

class FishingWorkTest {

    private val near = spot(3100, 3092, distance = 2)
    private val far = spot(3104, 3092, distance = 6)
    private val inReach = near.copy(distance = 0, usableFromHere = true)
    private val anchor = Tile(3102, 3095)

    private fun spot(x: Int, y: Int, distance: Int, usable: Boolean = false) =
        Candidate(id = x, position = Position(x, y), distance, usable, approach = Position(x, y + 1))

    private fun view(spots: List<Candidate> = listOf(near, far), atLocation: Boolean = true) = FishingView(atLocation, spots)

    private val fisher = FakeFisher(view(), anchor)
    private val work = FishingWork(fisher)

    @Test
    fun `the meter reads the catches carried`() {
        fisher.catches = 4

        assertEquals(4, (work.meter as Meter.Rises).reading())
    }

    @Test
    fun `fishing has nothing to settle before the step may end`() {
        assertFalse(work.finishing())
    }

    @Test
    fun `the work is busy while the fisher is`() {
        fisher.busy = true

        assertTrue(work.isBusy())
    }

    @Test
    fun `interrupting breaks off the fishing`() {
        work.interrupt()

        assertEquals(listOf("interrupt"), fisher.steps)
    }

    @Test
    fun `a spot in reach is fished before a nearer one out of reach, which is work`() {
        fisher.view = view(listOf(far, inReach))

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Worked to listOf("fish 3100,3092"), outcome to fisher.steps)
    }

    @Test
    fun `the nearest spot out of reach is walked to, which approaches it`() {
        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Approached to listOf("walk to 3100,3092"), outcome to fisher.steps)
    }

    @Test
    fun `away from the area with no spot in sight the step goes back to its anchor`() {
        fisher.view = view(emptyList(), atLocation = false)

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Away(anchor) to emptyList<String>(), outcome to fisher.steps)
    }

    @Test
    fun `inside the area with no spot in reach the step waits for one, clicking nothing`() {
        fisher.view = view(emptyList())

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Waiting(Reason.NoneInReach(Target.FISHING_SPOT)) to emptyList<String>(), outcome to fisher.steps)
    }

    @Test
    fun `a spot set aside is left out for the next one`() {
        assertEquals(WalkTo(far), FishingPlanner.decide(view(listOf(inReach, far)), skipped = setOf(inReach.aim)))
    }

    @Test
    fun `with every spot in reach set aside the step blocks instead of waiting`() {
        val decision = FishingPlanner.decide(view(listOf(inReach)), skipped = setOf(inReach.aim))

        assertEquals(FishingDecision.Settled(StepOutcome.Blocked(Reason.NoneInReach(Target.FISHING_SPOT))), decision)
    }
}
