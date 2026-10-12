package game.idle.autopilot.fighting

import game.idle.autopilot.fighting.FightDecision.Settled
import game.idle.autopilot.fighting.FightDecision.WalkTo
import game.idle.flow.Meter
import game.idle.flow.Reason
import game.idle.flow.StepOutcome
import game.idle.flow.Target
import game.idle.location.Tile
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FightWorkTest {

    private val near = target(3100, 9510, distance = 2)
    private val far = target(3104, 9510, distance = 6)
    private val inReach = near.copy(distance = 0, usableFromHere = true)
    private val anchor = Tile(3102, 9512)

    private fun target(x: Int, y: Int, distance: Int, index: Int = x) =
        TargetCandidate(npcIndex = index, position = Position(x, y), distance, usableFromHere = false, approach = Position(x, y + 1))

    private fun view(atLocation: Boolean = true, targets: List<TargetCandidate> = listOf(near, far)) = FightView(atLocation, targets)

    private val fighter = FakeFighter(view(), anchor)
    private val work = FightWork(fighter)

    @Test
    fun `the meter reads the kills`() {
        fighter.kills = 3

        assertEquals(3, (work.meter as Meter.Rises).reading())
    }

    @Test
    fun `the work is finishing while the kill made last is dying`() {
        fighter.lastKillDying = true

        assertTrue(work.finishing())
    }

    @Test
    fun `with no kill dying the work is not finishing`() {
        assertFalse(work.finishing())
    }

    @Test
    fun `the work is busy while the fighter is`() {
        fighter.busy = true

        assertTrue(work.isBusy())
    }

    @Test
    fun `interrupting breaks off the fight`() {
        work.interrupt()

        assertEquals(listOf("interrupt"), fighter.steps)
    }

    @Test
    fun `an npc in reach is attacked, which is work`() {
        fighter.view = view(targets = listOf(inReach))

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Worked to listOf("attack 3100,9510"), outcome to fighter.steps)
    }

    @Test
    fun `with no npc in reach the nearest is walked up to, which approaches it`() {
        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Approached to listOf("walk to 3100,9510"), outcome to fighter.steps)
    }

    @Test
    fun `an npc in reach is attacked before a nearer one out of reach`() {
        fighter.view = view(targets = listOf(near.copy(distance = 1), inReach.copy(position = Position(3108, 9510))))

        work.act(left = null)

        assertEquals(listOf("attack 3108,9510"), fighter.steps)
    }

    @Test
    fun `an npc attacking the player is fought back before one nearer and in reach`() {
        fighter.view = view(targets = listOf(inReach, far.copy(attacking = true)))

        work.act(left = null)

        assertEquals(listOf("walk to 3104,9510"), fighter.steps)
    }

    @Test
    fun `npcs at the same distance are picked west to east, then south to north`() {
        fighter.view = view(targets = listOf(target(3102, 9511, 2), target(3101, 9515, 2), target(3101, 9513, 2, index = 1)))

        work.act(left = null)

        assertEquals(listOf("walk to 3101,9513"), fighter.steps)
    }

    @Test
    fun `away from the area with nothing to fight the step goes back to its anchor`() {
        fighter.view = view(atLocation = false, targets = emptyList())

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Away(anchor) to emptyList<String>(), outcome to fighter.steps)
    }

    @Test
    fun `inside the area with no npc in reach the step waits for one, clicking nothing`() {
        fighter.view = view(targets = emptyList())

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Waiting(Reason.NoneInReach(Target.NPC)) to emptyList<String>(), outcome to fighter.steps)
    }

    @Test
    fun `an npc set aside is left out for the next one`() {
        val decision = FightingPlanner.decide(view(targets = listOf(inReach, far)), skipped = setOf(inReach.aim))

        assertEquals(WalkTo(far), decision)
    }

    @Test
    fun `with every npc around set aside the planner waits, never blocks`() {
        val decision = FightingPlanner.decide(view(targets = listOf(inReach)), skipped = setOf(inReach.aim))

        assertEquals(Settled(StepOutcome.Waiting(Reason.NoneInReach(Target.NPC))), decision)
    }

    @Test
    fun `an npc attacked twice with no kill in between is set aside for the next`() {
        fighter.view = view(targets = listOf(inReach, far))
        work.act(left = null)

        work.act(left = null)

        assertEquals(listOf("attack 3100,9510", "walk to 3104,9510"), fighter.steps)
    }

    @Test
    fun `an npc walked to twice with no kill in between is set aside for the next`() {
        work.act(left = null)

        work.act(left = null)

        assertEquals(listOf("walk to 3100,9510", "walk to 3104,9510"), fighter.steps)
    }

    @Test
    fun `an npc set aside is still left out once it wandered onto another tile`() {
        work.act(left = null)
        work.act(left = null)
        fighter.view = view(targets = listOf(near.copy(position = Position(3100, 9511)), far.copy(distance = 5)))

        work.act(left = null)

        assertEquals(listOf("walk to 3100,9510", "walk to 3104,9510", "walk to 3104,9510"), fighter.steps)
    }

    @Test
    fun `attacking another npc next is no retry`() {
        fighter.view = view(targets = listOf(inReach))
        work.act(left = null)
        fighter.view = view(targets = listOf(inReach.copy(npcIndex = 1, position = Position(3101, 9510))))

        work.act(left = null)

        assertEquals(listOf("attack 3100,9510", "attack 3101,9510"), fighter.steps)
    }

    @Test
    fun `an npc that moved since it was walked to is no retry`() {
        fighter.view = view(targets = listOf(near))
        work.act(left = null)
        fighter.view = view(targets = listOf(near.copy(position = Position(3100, 9511))))

        work.act(left = null)

        assertEquals(listOf("walk to 3100,9510", "walk to 3100,9511"), fighter.steps)
    }

    @Test
    fun `a lone npc set aside comes back at once, every npc around being set aside`() {
        fighter.view = view(targets = listOf(inReach))
        work.act(left = null)

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Worked to listOf("attack 3100,9510", "attack 3100,9510"), outcome to fighter.steps)
    }

    @Test
    fun `an npc walked to and then in reach is attacked`() {
        work.act(left = null)
        fighter.view = view(targets = listOf(inReach, far))

        work.act(left = null)

        assertEquals(listOf("walk to 3100,9510", "attack 3100,9510"), fighter.steps)
    }

    @Test
    fun `an npc attacked again after a kill is not set aside`() {
        fighter.view = view(targets = listOf(inReach, far))
        work.act(left = null)
        fighter.kills = 1

        work.act(left = null)

        assertEquals(listOf("attack 3100,9510", "attack 3100,9510"), fighter.steps)
    }

    @Test
    fun `a kill brings the npcs set aside back`() {
        fighter.view = view(targets = listOf(inReach, far))
        work.act(left = null)
        work.act(left = null)
        fighter.kills = 1

        work.act(left = null)

        assertEquals("attack 3100,9510", fighter.steps.last())
    }
}
