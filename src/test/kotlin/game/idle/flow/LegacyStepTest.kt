package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LegacyStepTest {

    /** A legacy activity whose every answer is set by the test; [counting] gives it a count. */
    private open class Old : LegacyActivity {
        var busy = false
        var done = false
        var blocked: Reason? = null
        var stop: StepStop? = null
        var said: Said? = null
        var acts = 0

        override fun isBusy(): Boolean = busy

        override fun act() {
            acts++
        }

        override fun isDone(): Boolean = done

        override fun blocked(): Reason? = blocked

        override fun stopping(): StepStop? = stop

        override fun said(): Said? = said
    }

    private class Counting(private val count: Int) : Old(), CountsAmount {
        override fun amountDone(): Int = count
    }

    private val old = Old()
    private val step = LegacyStep(old)

    @Test
    fun `busy, done, block, stop and what is said are the old activity's`() {
        old.busy = true
        old.done = true
        old.blocked = Reason.NoneInReach(Target.ANVIL)
        old.stop = StepStop.Stuck(Reason.BankFull)
        old.said = Said.Skipped(Reason.BankFull)

        val reported = listOf(step.isBusy(), step.isDone(), step.blocked(), step.stopping(), step.said())

        assertEquals(listOf(true, true, Reason.NoneInReach(Target.ANVIL), StepStop.Stuck(Reason.BankFull), Said.Skipped(Reason.BankFull)), reported)
    }

    @Test
    fun `acting acts the old activity`() {
        step.act()

        assertEquals(1, old.acts)
    }

    @Test
    fun `an old activity that counts gives its count`() {
        assertEquals(3, LegacyStep(Counting(3)).amountDone())
    }

    @Test
    fun `an old activity that counts nothing has done nothing`() {
        assertEquals(0, step.amountDone())
    }

    @Test
    fun `an old activity never waits and always counts as having worked`() {
        assertEquals(null to true, step.waiting() to step.worked())
    }

    @Test
    fun `ending the turn early does nothing for an old activity`() {
        step.endTurn()

        assertFalse(step.isDone())
    }

    @Test
    fun `an old activity with nothing to say says nothing`() {
        assertNull(step.said())
    }
}
