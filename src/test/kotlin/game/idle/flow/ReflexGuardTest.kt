package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReflexGuardTest {

    private val log = mutableListOf<String>()
    private val step = FakeStepActivity("fight", log)
    private val body = FakeReflexBody()
    private val trout = 333
    private val lobster = 379

    private fun below(percent: Int) = ReflexTrigger.HitpointsBelow(percent)

    private fun eat(number: Int = 1, foods: Set<Int> = emptySet(), percent: Int = 50) = ResolvedReflex(number, below(percent), ReflexAction.Eat(foods))

    private fun runAway(number: Int = 2, then: ReflexThen = ReflexThen.StopFlow) = ResolvedReflex(number, below(50), ReflexAction.RunAway(then))

    private fun guard(vararg reflexes: ResolvedReflex) = ReflexGuard(step, reflexes.toList(), body)

    private val hurtTo = Health(9, 20)

    private fun hurt() {
        body.health = hurtTo
    }

    @Test
    fun `with no reflex firing the step acts`() {
        val guard = guard(eat())
        body.bag[0] = trout

        guard.act()

        assertEquals(listOf("fight:1"), log)
    }

    @Test
    fun `an eat reflex eats the first food in the bag`() {
        val guard = guard(eat())
        body.bag[3] = trout
        body.bag[1] = lobster
        hurt()

        guard.act()

        assertEquals(listOf("eat 1"), body.done)
    }

    @Test
    fun `while a reflex fires the step does not act`() {
        val guard = guard(eat())
        body.bag[0] = trout
        hurt()

        guard.act()

        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `an eat reflex eats only its foods`() {
        val guard = guard(eat(foods = setOf(trout)))
        body.bag[1] = lobster
        body.bag[3] = trout
        hurt()

        guard.act()

        assertEquals(listOf("eat 3"), body.done)
    }

    @Test
    fun `an eat reflex with none of its foods left lets the step act`() {
        val guard = guard(eat(foods = setOf(trout)))
        body.bag[1] = lobster
        hurt()

        guard.act()

        assertEquals(listOf("fight:1"), log)
    }

    @Test
    fun `an eat reflex with no food left falls through to the next`() {
        val guard = guard(eat(), runAway())
        hurt()

        guard.act()

        assertEquals(StepStop.Reflex(2, hurtTo), guard.stopping())
    }

    @Test
    fun `the first reflex in the step's order fires`() {
        val guard = guard(runAway(number = 1), eat(number = 2))
        body.bag[0] = trout
        hurt()

        guard.act()

        assertEquals(StepStop.Reflex(1, hurtTo), guard.stopping())
    }

    @Test
    fun `a reflex whose hitpoints have not fallen that far does not fire`() {
        val guard = guard(eat(number = 1, percent = 25), eat(number = 2, foods = setOf(trout)))
        body.bag[0] = lobster
        body.bag[1] = trout
        hurt()

        guard.act()

        assertEquals(listOf("eat 1"), body.done)
    }

    @Test
    fun `running away while attacked flees`() {
        val guard = guard(runAway())
        body.attacked = true
        hurt()

        guard.act()

        assertEquals(listOf("flee"), body.done)
        assertNull(guard.stopping())
    }

    @Test
    fun `a run goes on while attacked, healed or not`() {
        val guard = guard(runAway())
        body.attacked = true
        hurt()
        guard.act()
        body.health = Health(20, 20)

        guard.act()

        assertEquals(listOf("flee", "flee"), body.done)
    }

    @Test
    fun `once nothing attacks the run ends with its then`() {
        val guard = guard(runAway(then = ReflexThen.JumpTo(1)))
        body.attacked = true
        hurt()
        guard.act()
        body.attacked = false

        guard.act()

        assertEquals(ReflexOutcome.Jump(index = 1, number = 2, health = hurtTo), guard.jump())
    }

    @Test
    fun `the run's then gives the hitpoints the reflex fired at`() {
        val guard = guard(runAway())
        body.attacked = true
        hurt()
        guard.act()
        body.health = Health(12, 20)
        body.attacked = false

        guard.act()

        assertEquals(StepStop.Reflex(2, hurtTo), guard.stopping())
    }

    @Test
    fun `with nothing attacking a run goes straight to its then`() {
        val guard = guard(runAway())
        hurt()

        guard.act()

        assertEquals(listOf("calm"), body.done)
        assertEquals(StepStop.Reflex(2, hurtTo), guard.stopping())
    }

    @Test
    fun `a run that ends puts the player's running back before its then`() {
        val guard = guard(runAway())
        body.attacked = true
        hurt()
        guard.act()
        body.attacked = false

        guard.act()

        assertEquals(listOf("flee", "calm"), body.done)
    }

    @Test
    fun `a run that stops the flow sends nobody anywhere`() {
        val guard = guard(runAway())
        hurt()

        guard.act()

        assertNull(guard.jump())
    }

    @Test
    fun `a step going on has no jump and its own reason to stop`() {
        val guard = guard()
        step.stop = StepStop.Stuck(Reason.BankFull)

        guard.act()

        assertNull(guard.jump())
        assertEquals(StepStop.Stuck(Reason.BankFull), guard.stopping())
    }

    @Test
    fun `what the step says is the guard's`() {
        step.said = Said.Skipped(Reason.BankFull)

        assertEquals(Said.Skipped(Reason.BankFull), guard().said())
    }

    @Test
    fun `a busy step with no reflex firing keeps the guard busy`() {
        step.busy = true

        assertTrue(guard(eat()).isBusy())
    }

    @Test
    fun `a step that is not busy lets the guard act`() {
        assertFalse(guard().isBusy())
    }

    @Test
    fun `a reflex that would fire lets the guard act mid-fight`() {
        step.busy = true
        body.bag[0] = trout
        hurt()

        assertFalse(guard(eat()).isBusy())
    }

    @Test
    fun `a run on lets the guard act at each decision`() {
        val guard = guard(runAway())
        body.attacked = true
        hurt()
        guard.act()
        body.health = Health(20, 20)
        step.busy = true

        assertFalse(guard.isBusy())
    }

    @Test
    fun `the step's end and block are the guard's`() {
        step.done = true
        step.blocked = Reason.NoneInReach(Target.NPC)

        val guard = guard()

        assertEquals(true to Reason.NoneInReach(Target.NPC), guard.isDone() to guard.blocked())
    }

    @Test
    fun `the step's count is the guard's`() {
        step.amountDone = 3

        assertEquals(3, guard().amountDone())
    }

    @Test
    fun `the step's wait and whether it worked are the guard's`() {
        step.waiting = Reason.NoneInReach(Target.NPC)
        step.worked = false

        val guard = guard()

        assertEquals(Reason.NoneInReach(Target.NPC) to false, guard.waiting() to guard.worked())
    }

    @Test
    fun `ending the turn ends the step's`() {
        guard().endTurn()

        assertEquals(1, step.ended)
    }

    @Test
    fun `health below a share compares against full hitpoints`() {
        assertTrue(Health(7, 10).below(75))
        assertFalse(Health(5, 10).below(50))
    }
}
