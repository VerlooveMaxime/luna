package game.idle.autopilot.woodcutting

import game.idle.flow.Meter
import game.idle.flow.Reason
import game.idle.flow.StepOutcome
import game.idle.flow.Target
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WoodcuttingWorkTest {

    private val nearTree = tree(3171, 3444, distance = 2)
    private val farTree = tree(3170, 3454, distance = 8)
    private val nearTreeInReach = nearTree.copy(distance = 1, usableFromHere = true)
    private val anchor = Tile(3175, 3448)

    private val woodcutter = FakeWoodcutter(view(listOf(nearTree, farTree)), anchor)
    private val work = WoodcuttingWork(woodcutter, anyTree)

    @Test
    fun `the meter reads the logs carried`() {
        woodcutter.logs = 4

        assertEquals(4, (work.meter as Meter.Rises).reading())
    }

    @Test
    fun `a chop has nothing to settle before the step may end`() {
        assertFalse(work.finishing())
    }

    @Test
    fun `the work is busy while the woodcutter is`() {
        woodcutter.busy = true

        assertTrue(work.isBusy())
    }

    @Test
    fun `interrupting breaks off the chop`() {
        work.interrupt()

        assertEquals(listOf("interrupt"), woodcutter.steps)
    }

    @Test
    fun `a tree in reach is chopped, which is work`() {
        woodcutter.view = view(listOf(nearTreeInReach))

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Worked to listOf("chop 3171,3444"), outcome to woodcutter.steps)
    }

    @Test
    fun `a tree out of reach is walked to, which approaches it`() {
        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Approached to listOf("walk to 3171,3444"), outcome to woodcutter.steps)
    }

    @Test
    fun `away from the area with no tree in sight the step goes back to its anchor`() {
        woodcutter.view = view(emptyList(), atLocation = false)

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Away(anchor) to emptyList<String>(), outcome to woodcutter.steps)
    }

    @Test
    fun `inside the area with no tree in reach the step waits for one, clicking nothing`() {
        woodcutter.view = view(emptyList())

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Waiting(Reason.NoneInReach(Target.TREE)) to emptyList<String>(), outcome to woodcutter.steps)
    }
}
