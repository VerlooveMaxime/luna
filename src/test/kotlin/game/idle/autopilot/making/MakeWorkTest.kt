package game.idle.autopilot.making

import game.idle.flow.Meter
import game.idle.flow.Reason
import game.idle.flow.StepOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MakeWorkTest {

    private val ingredients = MakeView(useSlot = 0, onSlot = 1, windowOpen = false, productOption = null)
    private val window = MakeView(useSlot = 0, onSlot = 1, windowOpen = true, productOption = 0)

    private val maker = FakeMaker(ingredients)
    private val work = MakeWork(maker)

    @Test
    fun `the meter reads the products carried`() {
        maker.products = 4

        assertEquals(4, (work.meter as Meter.Rises).reading())
    }

    @Test
    fun `making has nothing to settle before the step may end`() {
        assertFalse(work.finishing())
    }

    @Test
    fun `the work is busy while the maker is`() {
        maker.busy = true

        assertTrue(work.isBusy())
    }

    @Test
    fun `interrupting breaks off the making`() {
        work.interrupt()

        assertEquals(listOf("interrupt"), maker.steps)
    }

    @Test
    fun `the two items are combined, which is work`() {
        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Worked to listOf("use 0 on 1"), outcome to maker.steps)
    }

    @Test
    fun `the make window is answered with the product, a full bag's worth without an amount`() {
        maker.view = window

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Worked to listOf("choose 0 x28"), outcome to maker.steps)
    }

    @Test
    fun `with an amount the make window is answered with what is left to make`() {
        maker.view = window

        work.act(left = 3)

        assertEquals(listOf("choose 0 x3"), maker.steps)
    }

    @Test
    fun `a window that does not offer the product blocks the step`() {
        maker.view = window.copy(productOption = null)

        assertEquals(StepOutcome.Blocked(Reason.WrongWindow), work.act(left = null))
    }

    @Test
    fun `missing the item to use, the step has nothing to work with`() {
        maker.view = ingredients.copy(useSlot = null)

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Empty to emptyList<String>(), outcome to maker.steps)
    }

    @Test
    fun `missing the item to use it on, the step has nothing to work with`() {
        maker.view = ingredients.copy(onSlot = null)

        assertEquals(StepOutcome.Empty, work.act(left = null))
    }

    @Test
    fun `combining again with nothing made since blocks the step instead`() {
        work.act(left = null)

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Blocked(Reason.CannotMake) to listOf("use 0 on 1"), outcome to maker.steps)
    }

    @Test
    fun `once it cannot make the product the step stops combining while nothing changes`() {
        work.act(left = null)
        work.act(left = null)

        work.act(left = null)

        assertEquals(listOf("use 0 on 1"), maker.steps)
    }

    @Test
    fun `combining again after something was made is work`() {
        work.act(left = null)
        maker.products = 1

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Worked to listOf("use 0 on 1", "use 0 on 1"), outcome to maker.steps)
    }

    @Test
    fun `a window answered in between starts the combining over`() {
        work.act(left = null)
        maker.view = window
        work.act(left = null)
        maker.view = ingredients

        work.act(left = null)

        assertEquals(listOf("use 0 on 1", "choose 0 x28", "use 0 on 1"), maker.steps)
    }

    @Test
    fun `a wrong window in between starts the combining over`() {
        work.act(left = null)
        maker.view = window.copy(productOption = null)
        work.act(left = null)
        maker.view = ingredients

        work.act(left = null)

        assertEquals(listOf("use 0 on 1", "use 0 on 1"), maker.steps)
    }
}
