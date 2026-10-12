package game.idle.autopilot.smithing

import game.idle.autopilot.smithing.SmithingDecision.Settled
import game.idle.autopilot.smithing.SmithingDecision.WalkTo
import game.idle.flow.Meter
import game.idle.flow.Reason
import game.idle.flow.StepOutcome
import game.idle.flow.Target
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmithingWorkTest {

    private val near = anvil(3201, 3200, distance = 2)
    private val far = anvil(3210, 3200, distance = 9)
    private val inReach = near.copy(distance = 0, usableFromHere = true)
    private val anchor = Tile(3203, 3203)

    private val smither = FakeSmither(smithingView(listOf(inReach)), anchor)
    private val work = SmithingWork(smither)

    @Test
    fun `the meter reads the items carried`() {
        smither.made = 4

        assertEquals(4, (work.meter as Meter.Rises).reading())
    }

    @Test
    fun `smithing has nothing to settle before the step may end`() {
        assertFalse(work.finishing())
    }

    @Test
    fun `the work is busy while the smither is`() {
        smither.busy = true

        assertTrue(work.isBusy())
    }

    @Test
    fun `interrupting breaks off the smithing`() {
        work.interrupt()

        assertEquals(listOf("interrupt"), smither.steps)
    }

    @Test
    fun `the bar is used on an anvil in reach, which is work`() {
        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Worked to listOf("use 0 on 3201,3200"), outcome to smither.steps)
    }

    @Test
    fun `with the window open the item is picked, which is work`() {
        smither.view = smithingView(listOf(inReach), windowOpen = true)

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Worked to listOf("choose 10"), outcome to smither.steps)
    }

    @Test
    fun `with an amount the window's largest count that does not pass what is left is picked`() {
        smither.view = smithingView(listOf(inReach), windowOpen = true)

        work.act(left = 7)

        assertEquals(listOf("choose 5"), smither.steps)
    }

    @Test
    fun `a single item left is picked once`() {
        smither.view = smithingView(listOf(inReach), windowOpen = true)

        work.act(left = 1)

        assertEquals(listOf("choose 1"), smither.steps)
    }

    @Test
    fun `an anvil in reach comes before a nearer one out of reach`() {
        smither.view = smithingView(listOf(near.copy(distance = 1), inReach.copy(position = far.position)))

        work.act(left = null)

        assertEquals(listOf("use 0 on 3210,3200"), smither.steps)
    }

    @Test
    fun `out of reach, the nearest anvil is walked to, which approaches it`() {
        smither.view = smithingView(listOf(far, near))

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Approached to listOf("walk to 3201,3200"), outcome to smither.steps)
    }

    @Test
    fun `equally near anvils are picked west to east, then south to north`() {
        smither.view = smithingView(listOf(anvil(3201, 3205, distance = 2), near))

        work.act(left = null)

        assertEquals(listOf("walk to 3201,3200"), smither.steps)
    }

    @Test
    fun `away from the area with no anvil in sight the step goes back to its anchor`() {
        smither.view = smithingView(emptyList(), atLocation = false)

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Away(anchor) to emptyList<String>(), outcome to smither.steps)
    }

    @Test
    fun `inside the area with no anvil in reach the step blocks, clicking nothing`() {
        smither.view = smithingView(emptyList())

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Blocked(Reason.NoneInReach(Target.ANVIL)) to emptyList<String>(), outcome to smither.steps)
    }

    @Test
    fun `with too few bars for the item the step has nothing to work with`() {
        smither.view = smithingView(listOf(inReach), barSlot = null)

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Empty to emptyList<String>(), outcome to smither.steps)
    }

    @Test
    fun `an anvil set aside is left out for the next one`() {
        val decision = SmithingPlanner.decide(smithingView(listOf(inReach, far)), skipped = setOf(inReach.aim), left = null)

        assertEquals(WalkTo(far), decision)
    }

    @Test
    fun `with every anvil in reach set aside the step blocks instead of waiting`() {
        val decision = SmithingPlanner.decide(smithingView(listOf(inReach)), skipped = setOf(inReach.aim), left = null)

        assertEquals(Settled(StepOutcome.Blocked(Reason.NoneInReach(Target.ANVIL))), decision)
    }
}
