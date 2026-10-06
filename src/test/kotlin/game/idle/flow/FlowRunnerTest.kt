package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlowRunnerTest {

    private val player = FakeFlowPlayer()

    private fun runner(vararg names: String, startAt: Int = 0) = FlowRunner(names.map { FakeStep(it) }, startAt, player)

    @Test
    fun `the first act starts the first step and acts on it`() {
        runner("chop").act()

        assertEquals(listOf("chop:1"), player.log)
    }

    @Test
    fun `a step keeps acting until it is over`() {
        val runner = runner("chop")

        repeat(3) { runner.act() }

        assertEquals(listOf("chop:1", "chop:2", "chop:3"), player.log)
    }

    @Test
    fun `busy follows the current step`() {
        val runner = runner("chop")
        assertFalse(runner.isBusy())
        runner.act()

        player.started.single().busy = true

        assertTrue(runner.isBusy())
    }

    @Test
    fun `a step that says it is done hands over to the next`() {
        val runner = runner("chop", "bank")
        runner.act()
        runner.act()
        player.started[0].done = true

        runner.act()
        runner.act()

        assertEquals(listOf("chop:1", "chop:2", "bank:1"), player.log)
        assertEquals(listOf(1), player.savedSteps)
    }

    @Test
    fun `a step after the first runs its own activity`() {
        runner("chop", "drop", startAt = 1).act()

        assertEquals(listOf("drop:1"), player.log)
    }

    @Test
    fun `after the last step the flow starts over with a fresh activity`() {
        val runner = runner("chop", "bank", startAt = 1)
        runner.act()
        player.started[0].done = true

        runner.act()
        runner.act()

        assertEquals(listOf("bank:1", "chop:1"), player.log)
        assertEquals(listOf(0), player.savedSteps)
    }

    @Test
    fun `starting over counts a lap`() {
        val runner = runner("chop", "bank", startAt = 1)
        runner.act()
        player.started[0].done = true

        runner.act()

        assertEquals(1, player.laps)
    }

    @Test
    fun `moving to a step that is not the first counts no lap`() {
        val runner = runner("chop", "bank")
        runner.act()
        player.started[0].done = true

        runner.act()

        assertEquals(0, player.laps)
    }

    @Test
    fun `a one step flow repeats itself`() {
        val runner = runner("chop")
        runner.act()
        player.started[0].done = true

        runner.act()
        runner.act()

        assertEquals(listOf("chop:1", "chop:1"), player.log)
        assertEquals(2, player.started.size)
    }

    @Test
    fun `a saved step past the end starts at the last step`() {
        runner("chop", "bank", startAt = 7).act()

        assertEquals(listOf("bank:1"), player.log)
    }

    @Test
    fun `a negative saved step starts at the first`() {
        runner("chop", startAt = -3).act()

        assertEquals(listOf("chop:1"), player.log)
    }

    @Test
    fun `a flow starting at a saved step resumes there`() {
        runner("chop", "bank", startAt = 1).act()

        assertEquals(listOf("bank:1"), player.log)
    }

    @Test
    fun `an empty flow does nothing`() {
        val runner = runner()

        runner.act()

        assertFalse(runner.isBusy())
        assertEquals(emptyList<String>(), player.log)
    }
}
