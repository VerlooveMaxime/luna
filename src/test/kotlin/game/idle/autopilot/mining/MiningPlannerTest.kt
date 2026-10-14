package game.idle.autopilot.mining

import game.idle.autopilot.mining.MiningDecision.Mine
import game.idle.autopilot.mining.MiningDecision.Settled
import game.idle.autopilot.mining.MiningDecision.WalkTo
import game.idle.autopilot.mining.MiningDecision.WalkToLocation
import game.idle.flow.Aim
import game.idle.flow.Reason
import game.idle.flow.StepOutcome
import game.idle.flow.Target
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MiningPlannerTest {

    private val near = rock(3200, 3200, distance = 2)
    private val far = rock(3210, 3200, distance = 9)
    private val inReach = rock(3205, 3205, distance = 0, usableFromHere = true)

    private fun decide(view: MiningView, skipped: Set<Aim> = emptySet()) = MiningPlanner.decide(view, skipped)

    @Test
    fun `a rock in reach is mined before a nearer one out of reach`() {
        assertEquals(Mine(inReach), decide(miningView(listOf(near, inReach))))
    }

    @Test
    fun `with no rock in sight away from the work spot, the player walks back`() {
        assertEquals(WalkToLocation, decide(miningView(emptyList(), atLocation = false)))
    }

    @Test
    fun `with no rock at the work spot, the step waits for one to fill again`() {
        assertEquals(Settled(StepOutcome.Waiting(Reason.NoneInReach(Target.ROCK))), decide(miningView(emptyList())))
    }

    @Test
    fun `a rock set aside is left out for the next one`() {
        assertEquals(WalkTo(near), decide(miningView(listOf(inReach, near)), skipped = setOf(inReach.aim)))
    }

    @Test
    fun `with every rock in reach set aside the step blocks instead of waiting`() {
        assertEquals(Settled(StepOutcome.Blocked(Reason.NoneInReach(Target.ROCK))), decide(miningView(listOf(inReach)), skipped = setOf(inReach.aim)))
    }
}
