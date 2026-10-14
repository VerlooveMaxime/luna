package game.idle.autopilot.smelting

import game.idle.autopilot.smelting.SmeltingDecision.Settled
import game.idle.autopilot.smelting.SmeltingDecision.WalkTo
import game.idle.flow.Meter
import game.idle.flow.Reason
import game.idle.flow.StepOutcome
import game.idle.flow.Target
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmeltingWorkTest {

    private val near = furnace(3201, 3200, distance = 2)
    private val far = furnace(3210, 3200, distance = 9)
    private val inReach = near.copy(distance = 0, usableFromHere = true)
    private val anchor = Tile(3203, 3203)

    private val smelter = FakeSmelter(smeltingView(listOf(inReach)), anchor)
    private val work = SmeltingWork(smelter)

    @Test
    fun `the meter reads the bars carried`() {
        smelter.bars = 4

        assertEquals(4, (work.meter as Meter.Rises).reading())
    }

    @Test
    fun `smelting has nothing to settle before the step may end`() {
        assertFalse(work.finishing())
    }

    @Test
    fun `the work is busy while the smelter is`() {
        smelter.busy = true

        assertTrue(work.isBusy())
    }

    @Test
    fun `interrupting breaks off the smelting`() {
        work.interrupt()

        assertEquals(listOf("interrupt"), smelter.steps)
    }

    @Test
    fun `the ore is used on a furnace in reach, which is work`() {
        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Worked to listOf("smelt 0 on 3201,3200"), outcome to smelter.steps)
    }

    @Test
    fun `out of reach, the nearest furnace is walked to, which approaches it`() {
        smelter.view = smeltingView(listOf(far, near))

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Approached to listOf("walk to 3201,3200"), outcome to smelter.steps)
    }

    @Test
    fun `away from the area with no furnace in sight the step goes back to its anchor`() {
        smelter.view = smeltingView(emptyList(), atLocation = false)

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Away(anchor) to emptyList<String>(), outcome to smelter.steps)
    }

    @Test
    fun `inside the area with no furnace in reach the step blocks, clicking nothing`() {
        smelter.view = smeltingView(emptyList())

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Blocked(Reason.NoneInReach(Target.FURNACE)) to emptyList<String>(), outcome to smelter.steps)
    }

    @Test
    fun `with no ore for a bar the step has nothing to work with`() {
        smelter.view = smeltingView(listOf(inReach), oreSlot = null)

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Empty to emptyList<String>(), outcome to smelter.steps)
    }

    @Test
    fun `a furnace set aside is left out for the next one`() {
        val decision = SmeltingPlanner.decide(smeltingView(listOf(inReach, far)), skipped = setOf(inReach.aim))

        assertEquals(WalkTo(far), decision)
    }

    @Test
    fun `with every furnace in reach set aside the step blocks instead of waiting`() {
        val decision = SmeltingPlanner.decide(smeltingView(listOf(inReach)), skipped = setOf(inReach.aim))

        assertEquals(Settled(StepOutcome.Blocked(Reason.NoneInReach(Target.FURNACE))), decision)
    }
}
