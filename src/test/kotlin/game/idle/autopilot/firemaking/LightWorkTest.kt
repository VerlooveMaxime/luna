package game.idle.autopilot.firemaking

import game.idle.flow.Meter
import game.idle.flow.Reason
import game.idle.flow.StepOutcome
import game.idle.flow.Target
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LightWorkTest {

    private val ready = LightView(lightable = 4, tileFree = true)
    private val lighter = FakeLighter(ready)
    private val work = LightWork(lighter)

    @Test
    fun `the meter reads the logs carried, which fall as they are lit`() {
        lighter.logs = 3

        assertEquals(3, (work.meter as Meter.Falls).reading())
    }

    @Test
    fun `the work is busy while the lighter is`() {
        lighter.busy = true

        assertTrue(work.isBusy())
    }

    @Test
    fun `a fire still catching must settle before the step may end`() {
        lighter.busy = true

        assertTrue(work.finishing())
    }

    @Test
    fun `with no fire catching nothing holds the step's end`() {
        assertFalse(work.finishing())
    }

    @Test
    fun `interrupting leaves a fire being lit alone`() {
        work.interrupt()

        assertEquals(emptyList<String>(), lighter.steps)
    }

    @Test
    fun `a log is lit where the player stands, which is work`() {
        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Worked to listOf("light 4"), outcome to lighter.steps)
    }

    @Test
    fun `a taken tile is stepped off first, which approaches the work`() {
        lighter.view = ready.copy(tileFree = false)

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Approached to listOf("move to a free tile"), outcome to lighter.steps)
    }

    @Test
    fun `no free tile close enough blocks the step`() {
        lighter.view = ready.copy(tileFree = false)
        lighter.aside = false

        assertEquals(StepOutcome.Blocked(Reason.NoneInReach(Target.FREE_TILE)), work.act(left = null))
    }

    @Test
    fun `with no log to light the step has nothing to work with, lighting nothing`() {
        lighter.view = ready.copy(lightable = null)

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Empty to emptyList<String>(), outcome to lighter.steps)
    }
}
