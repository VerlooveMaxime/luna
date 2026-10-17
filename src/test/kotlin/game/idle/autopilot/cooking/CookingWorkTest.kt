package game.idle.autopilot.cooking

import game.idle.autopilot.cooking.CookingDecision.Settled
import game.idle.autopilot.cooking.CookingDecision.WalkTo
import game.idle.autopilot.travel.FakeTraveller
import game.idle.autopilot.travel.WalkUps
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

class CookingWorkTest {

    private val fire = cookingPlace(3100, 3095, distance = 3)
    private val farFire = cookingPlace(3106, 3095, distance = 7)
    private val fireInReach = fire.copy(distance = 0, usableFromHere = true)
    private val anchor = Tile(3102, 3098)

    private val cooker = FakeCooker(cookingView(listOf(fire, farFire)), anchor)
    /** Stands away from every target, so a walk up walks; its walks join the adapter's steps. */
    private val traveller = FakeTraveller(Position(3000, 3000), cooker.steps)
    private val work = CookingWork(cooker, WalkUps(traveller))

    @Test
    fun `the meter reads the raw food carried, which falls as it is cooked`() {
        cooker.raw = 4

        assertEquals(4, (work.meter as Meter.Falls).reading())
    }

    @Test
    fun `cooking has nothing to settle before the step may end`() {
        assertFalse(work.finishing())
    }

    @Test
    fun `the work is busy while the cooker is`() {
        cooker.busy = true

        assertTrue(work.isBusy())
    }

    @Test
    fun `interrupting breaks off the cooking`() {
        work.interrupt()

        assertEquals(listOf("interrupt"), cooker.steps)
    }

    @Test
    fun `raw food is used on a fire in reach, which is work`() {
        cooker.view = cookingView(listOf(farFire, fireInReach))

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Worked to listOf("use 2 on 3100,3095"), outcome to cooker.steps)
    }

    @Test
    fun `the nearest fire out of reach is walked to, which approaches it`() {
        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Approached to listOf("walk to 3099,3095"), outcome to cooker.steps)
    }

    @Test
    fun `an open cooking window is answered with cook all, which is work`() {
        cooker.view = cookingView(listOf(fire), windowOpen = true)

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Worked to listOf("cook all"), outcome to cooker.steps)
    }

    @Test
    fun `away from the area with no fire in sight the step goes back to its anchor`() {
        cooker.view = cookingView(emptyList(), atLocation = false)

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Away(anchor) to emptyList<String>(), outcome to cooker.steps)
    }

    @Test
    fun `inside the area with no fire in reach the step blocks, clicking nothing`() {
        cooker.view = cookingView(emptyList())

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Blocked(Reason.NoneInReach(Target.FIRE_OR_RANGE)) to emptyList<String>(), outcome to cooker.steps)
    }

    @Test
    fun `with no raw food the player can cook the step has nothing to work with`() {
        cooker.view = cookingView(listOf(fireInReach), rawSlot = null)

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Empty to emptyList<String>(), outcome to cooker.steps)
    }

    @Test
    fun `a place set aside is left out for the next one`() {
        val decision = CookingPlanner.decide(cookingView(listOf(fireInReach, farFire)), skipped = setOf(fireInReach.aim))

        assertEquals(WalkTo(farFire), decision)
    }

    @Test
    fun `with every place in reach set aside the step blocks instead of waiting`() {
        val decision = CookingPlanner.decide(cookingView(listOf(fireInReach)), skipped = setOf(fireInReach.aim))

        assertEquals(Settled(StepOutcome.Blocked(Reason.NoneInReach(Target.FIRE_OR_RANGE))), decision)
    }
}
