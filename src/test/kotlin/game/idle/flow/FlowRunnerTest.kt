package game.idle.flow

import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FlowRunnerTest {

    private val player = FakeFlowPlayer()
    private val body = player.body
    private val low = Health(4, 20)
    private val noAxe = Reason.NeedsTool(ToolNeed("axe", mapOf(1351 to 1), Skill.WOODCUTTING))
    private val stuck = StepStop.Stuck(Reason.NoneLeftInBank(1521))

    private fun runner(vararg names: String, startAt: Int = 0) = FlowRunner(ResolvedFlow(names.map { FakeStep(it) }), startAt, player, words())

    /** A flow of [names] whose first step has a reflex that runs away, then jumps to the step at [to]. */
    private fun jumping(vararg names: String, to: Int): FlowRunner {
        val runAway = ResolvedReflex(1, ReflexTrigger.HitpointsBelow(50), ReflexAction.RunAway(ReflexThen.JumpTo(to)))
        val steps = names.map { FakeStep(it) }
        return FlowRunner(ResolvedFlow(steps, listOf(listOf(runAway)) + steps.drop(1).map { emptyList() }), 0, player, words())
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
    fun `the current step's reason to stop is the flow's, worded with the step's number`() {
        val runner = runner("walk", "bank", startAt = 1)
        runner.act()
        player.started.single().stop = stuck

        runner.act()

        assertEquals("Autopilot: stopped, step 2 is stuck: no item 1521 left in the bank.", runner.stopReason())
    }

    @Test
    fun `a reflex that stops the flow is worded with its number and hitpoints`() {
        val runner = runner("fight")
        runner.act()
        player.started.single().stop = StepStop.Reflex(2, Health(9, 20))

        runner.act()

        assertEquals("Autopilot: stopped, reflex 2 ran at 9/20 hitpoints.", runner.stopReason())
    }

    @Test
    fun `a stop while the player is attacked waits, the player running`() {
        val runner = runner("fight")
        runner.act()
        player.started.single().stop = stuck
        body.attacked = true

        runner.act()

        assertEquals(null, runner.stopReason())
        assertEquals(listOf("flee"), body.done)
    }

    @Test
    fun `once nothing attacks a stop that waited stops the flow, the player's running back as before`() {
        val runner = runner("fight")
        runner.act()
        player.started.single().stop = stuck
        body.attacked = true
        runner.act()
        body.attacked = false

        runner.act()

        assertEquals("Autopilot: stopped, step 1 is stuck: no item 1521 left in the bank.", runner.stopReason())
        assertEquals(listOf("flee", "calm"), body.done)
    }

    @Test
    fun `a flow waiting to stop acts at each decision, its step busy or not`() {
        val runner = runner("fight")
        runner.act()
        player.started.single().busy = true

        player.started.single().stop = stuck

        assertFalse(runner.isBusy())
    }

    @Test
    fun `a flow waiting to stop no longer works its step`() {
        val runner = runner("fight")
        runner.act()
        player.started.single().stop = stuck
        body.attacked = true

        runner.act()

        assertEquals(listOf("fight:1"), player.log)
    }

    @Test
    fun `the current step's block is the flow's, in words`() {
        val runner = runner("chop")
        runner.act()

        player.started.single().blocked = noAxe

        assertEquals("Needs an axe you can use", runner.blocked())
    }

    @Test
    fun `what the current step waits for is the flow's, in the block's words`() {
        val runner = runner("chop")
        runner.act()

        player.started.single().waiting = Reason.NoneInReach(Target.TREE)

        assertEquals("No tree in reach", runner.waiting())
    }

    @Test
    fun `nothing is waited for between two steps`() {
        val runner = runner("chop", "drop")
        runner.act()
        player.started.single().waiting = Reason.NoneInReach(Target.TREE)
        player.started.single().done = true

        runner.act()

        assertEquals(null, runner.waiting())
    }

    @Test
    fun `nothing is blocked between two steps`() {
        val runner = runner("chop", "drop")
        runner.act()
        player.started.single().blocked = noAxe
        player.started.single().done = true

        runner.act()

        assertEquals(null, runner.blocked())
    }

    @Test
    fun `what a step says after it acts is told, worded with its number`() {
        val runner = runner("fight", "pickup", startAt = 1)
        runner.act()
        player.started.single().said = Said.Ended(Reason.BagFull(listOf(526)))

        runner.act()

        assertEquals(listOf("Autopilot: step 2 ended, the bag is full: item 526 left lying."), player.told)
    }

    @Test
    fun `a step that says nothing tells nothing`() {
        val runner = runner("fight")

        runner.act()

        assertEquals(emptyList<String>(), player.told)
    }

    @Test
    fun `a step over before its turn says nothing`() {
        val runner = runner("bank", "fight")
        runner.act()
        player.started.single().done = true
        player.started.single().said = Said.Skipped(Reason.BankFull)

        runner.act()

        assertEquals(emptyList<String>(), player.told)
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
    fun `a lap in which no step worked stops the flow instead of counting`() {
        val runner = runner("chop", "bank", startAt = 1)
        runner.act()
        player.started[0].worked = false
        player.started[0].done = true

        runner.act()

        assertEquals(StepWords.IDLE_LAP to 0, runner.stopReason() to player.laps)
    }

    @Test
    fun `a lap in which a step worked is counted and the flow goes on`() {
        val runner = runner("chop", "bank", startAt = 1)
        runner.act()
        player.started[0].worked = true
        player.started[0].done = true

        runner.act()

        assertEquals(null to 1, runner.stopReason() to player.laps)
    }

    @Test
    fun `work earlier in the lap counts for it`() {
        val runner = runner("chop", "bank")
        runner.act()
        player.started[0].done = true
        runner.act()
        runner.act()
        player.started[1].worked = false
        player.started[1].done = true

        runner.act()

        assertEquals(1, player.laps)
    }

    @Test
    fun `each lap is judged on its own`() {
        val runner = runner("chop")
        runner.act()
        player.started[0].done = true
        runner.act()
        runner.act()
        player.started[1].worked = false
        player.started[1].done = true

        runner.act()

        assertEquals(StepWords.IDLE_LAP to 1, runner.stopReason() to player.laps)
    }

    @Test
    fun `work on a step a jump cut short counts for the lap`() {
        val runner = jumping("fight", "bank", to = 1)
        runner.act()
        hurt()
        runner.act()
        healed()
        runner.act()
        runner.act()
        player.started[1].worked = false
        player.started[1].done = true

        runner.act()

        assertEquals(null to 1, runner.stopReason() to player.laps)
    }

    @Test
    fun `a step cut short before it worked counts for nothing`() {
        val runner = jumping("fight", "bank", to = 1)
        runner.act()
        player.started[0].worked = false
        hurt()
        runner.act()
        healed()
        runner.act()
        player.started[1].worked = false
        player.started[1].done = true

        runner.act()

        assertEquals(StepWords.IDLE_LAP, runner.stopReason())
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
        val runner = FlowRunner(ResolvedFlow(listOf(FakeStep("fight")), listOf(listOf(stop))), 0, player, words())
        hurt()

        runner.act()

        assertEquals("Autopilot: stopped, reflex 2 ran at 4/20 hitpoints.", runner.stopReason())
    }

    @Test
    fun `the player's death stops the flow`() {
        val runner = runner("fight")
        runner.act()

        body.dead = true

        assertEquals(StepWords.DIED, runner.stopReason())
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
