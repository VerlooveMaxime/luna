package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FlowRunnerTest {

    private val player = FakeFlowPlayer()
    private val body = player.body
    private val low = Health(4, 20)

    private fun runner(vararg names: String, startAt: Int = 0) = FlowRunner(ResolvedFlow(names.map { FakeStep(it) }), startAt, player)

    /** A flow of [names] whose first step has a reflex that runs away, then jumps to the step at [to]. */
    private fun jumping(vararg names: String, to: Int): FlowRunner {
        val runAway = ResolvedReflex(1, ReflexTrigger.HitpointsBelow(50), ReflexAction.RunAway(ReflexThen.JumpTo(to)))
        val steps = names.map { FakeStep(it) }
        return FlowRunner(ResolvedFlow(steps, listOf(listOf(runAway)) + steps.drop(1).map { emptyList() }), 0, player)
    }

    /** The first step's reflex fires on the next act. */
    private fun hurt() {
        body.health = low
    }

    private fun healed() {
        body.health = Health(20, 20)
    }

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
    fun `the current step's reason to stop is the flow's`() {
        val runner = runner("fight")
        runner.act()

        player.started.single().stop = "out of food"

        assertEquals("out of food", runner.stopReason())
    }

    @Test
    fun `the current step's block is the flow's`() {
        val runner = runner("chop")
        runner.act()

        player.started.single().blocked = "Autopilot: you need an axe."

        assertEquals("Autopilot: you need an axe.", runner.blocked())
    }

    @Test
    fun `nothing is blocked between two steps`() {
        val runner = runner("chop", "drop")
        runner.act()
        player.started.single().blocked = "Autopilot: you need an axe."
        player.started.single().done = true

        runner.act()

        assertEquals(null, runner.blocked())
    }

    @Test
    fun `a step never stops the flow unless it says why`() {
        val plain = object : StepActivity {
            override fun isBusy() = false
            override fun isDone() = false
            override fun act() = Unit
        }

        assertEquals(null, plain.stopReason())
    }

    @Test
    fun `a flow that has not started a step has no reason to stop`() {
        assertEquals(null, runner("fight").stopReason())
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

    @Test
    fun `a step's reflexes guard it`() {
        val runner = jumping("fight", "bank", to = 1)
        hurt()

        runner.act()

        assertEquals(emptyList<String>(), player.log)
    }

    @Test
    fun `a step without reflexes acts at any hitpoints`() {
        val runner = runner("fight")
        hurt()

        runner.act()

        assertEquals(listOf("fight:1"), player.log)
    }

    @Test
    fun `a reflex's jump goes on from the step it names`() {
        val runner = jumping("fight", "walk", "bank", to = 2)
        hurt()
        runner.act()

        runner.act()

        assertEquals(listOf("bank:1"), player.log)
        assertEquals(listOf(2), player.savedSteps)
    }

    @Test
    fun `a jump says why`() {
        hurt()

        jumping("fight", "bank", to = 1).act()

        assertEquals(listOf("Autopilot: reflex 1 ran at 4/20 hitpoints, jumping to step 2."), player.told)
    }

    @Test
    fun `a jump back to the first step counts no lap`() {
        val runner = jumping("fight", "bank", to = 0)
        hurt()

        runner.act()

        assertEquals(0, player.laps)
        assertEquals(listOf(0), player.savedSteps)
    }

    @Test
    fun `a step cut short again before it made progress stops the flow`() {
        val runner = jumping("fight", "bank", to = 1)
        hurt()
        runner.act()
        runner.act()
        player.started[1].done = true
        runner.act()

        runner.act()

        assertEquals("Autopilot: stopped, step 1 was cut short again before it made progress.", runner.stopReason())
    }

    @Test
    fun `a step cut short again stops the flow without jumping`() {
        val runner = jumping("fight", "bank", to = 1)
        hurt()
        runner.act()
        runner.act()
        player.started[1].done = true
        runner.act()

        runner.act()

        assertEquals(listOf(1, 0), player.savedSteps)
        assertEquals(1, player.told.size)
    }

    @Test
    fun `a step that made progress before it was cut short again goes on`() {
        val runner = jumping("fight", "bank", to = 1)
        hurt()
        runner.act()
        runner.act()
        player.started[1].done = true
        runner.act()
        healed()
        runner.act()
        player.started[2].amountDone = 1
        hurt()

        runner.act()

        assertEquals(null, runner.stopReason())
        assertEquals(listOf(1, 0, 1), player.savedSteps)
    }

    @Test
    fun `a step that ended by itself since it was cut short starts afresh`() {
        val runner = jumping("fight", "bank", to = 1)
        hurt()
        runner.act()
        runner.act()
        player.started[1].done = true
        runner.act()
        healed()
        runner.act()
        player.started[2].done = true
        runner.act()
        runner.act()
        player.started[3].done = true
        runner.act()
        hurt()

        runner.act()

        assertEquals(null, runner.stopReason())
    }

    @Test
    fun `a reflex that stops the flow stops it`() {
        val stop = ResolvedReflex(2, ReflexTrigger.HitpointsBelow(50), ReflexAction.RunAway(ReflexThen.StopFlow))
        val runner = FlowRunner(ResolvedFlow(listOf(FakeStep("fight")), listOf(listOf(stop))), 0, player)
        hurt()

        runner.act()

        assertEquals("Autopilot: stopped by reflex 2 at 4/20 hitpoints.", runner.stopReason())
    }

    @Test
    fun `the player's death stops the flow`() {
        val runner = runner("fight")
        runner.act()

        body.dead = true

        assertEquals(FlowRunner.DIED, runner.stopReason())
    }

    @Test
    fun `a flow resolved without reflexes has none for each step`() {
        assertEquals(listOf(emptyList<ResolvedReflex>()), ResolvedFlow(listOf(FakeStep("chop"))).reflexes)
    }

    @Test
    fun `a flow resolved with reflexes for some steps only is refused`() {
        assertThrows<IllegalArgumentException> { ResolvedFlow(listOf(FakeStep("chop"), FakeStep("drop")), listOf(emptyList())) }
    }
}
