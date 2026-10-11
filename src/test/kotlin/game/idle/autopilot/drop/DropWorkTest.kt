package game.idle.autopilot.drop

import game.idle.flow.Meter
import game.idle.flow.StepOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DropWorkTest {

    private class FakeItemDropper(var items: Int) : ItemDropper {
        var busy = false
        var drops = 0
        var interrupted = 0

        override fun isBusy(): Boolean = busy

        override fun hasItems(): Boolean = items > 0

        override fun dropItems() {
            drops++
            items = 0
        }

        override fun interrupt() {
            interrupted++
        }
    }

    private val dropper = FakeItemDropper(items = 3)
    private val work = DropWork(dropper)

    @Test
    fun `the work is busy while the dropper is`() {
        dropper.busy = true

        assertTrue(work.isBusy())
    }

    @Test
    fun `with items in the bag a decision drops them, which is the work`() {
        val outcome = work.act(null)

        assertEquals(1 to StepOutcome.Worked, dropper.drops to outcome)
    }

    @Test
    fun `with none of its items left the drop is finished`() {
        dropper.items = 0

        val outcome = work.act(null)

        assertEquals(StepOutcome.Finished to 0, outcome to dropper.drops)
    }

    @Test
    fun `dropping counts nothing and has no unit settling`() {
        assertEquals(Meter.None to false, work.meter to work.finishing())
    }

    @Test
    fun `interrupting breaks off the dropper's action`() {
        work.interrupt()

        assertEquals(1, dropper.interrupted)
    }
}
