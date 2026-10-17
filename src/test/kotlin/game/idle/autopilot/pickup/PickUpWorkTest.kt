package game.idle.autopilot.pickup

import game.idle.autopilot.Candidate
import game.idle.autopilot.travel.FakeTraveller
import game.idle.autopilot.travel.WalkUps
import game.idle.flow.Meter
import game.idle.flow.StepOutcome
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PickUpWorkTest {

    private val bones = 526
    private val cowhide = 1739
    private val bonesUnderfoot = Candidate(bones, Position(3200, 3200), distance = 0, usableFromHere = true, approach = Position(3200, 3200))
    private val cowhideNear = Candidate(cowhide, Position(3203, 3200), distance = 3, usableFromHere = false, approach = Position(3203, 3200))
    private val picker = FakePicker(PickUpView(atLocation = true, items = listOf(bonesUnderfoot)))
    /** Stands away from every target, so a walk up walks; its walks join the adapter's steps. */
    private val traveller = FakeTraveller(Position(3000, 3000), picker.steps)
    private val work = PickUpWork(picker, WalkUps(traveller))

    @Test
    fun `the meter reads the pickups`() {
        picker.pickedUp = 4

        assertEquals(4, (work.meter as Meter.Rises).reading())
    }

    @Test
    fun `a take has nothing to settle before the step may end`() {
        assertFalse(work.finishing())
    }

    @Test
    fun `the work is busy while the picker is`() {
        picker.busy = true

        assertTrue(work.isBusy())
    }

    @Test
    fun `interrupting breaks off the take`() {
        work.interrupt()

        assertEquals(listOf("interrupt"), picker.steps)
    }

    @Test
    fun `an item underfoot is taken, which is work`() {
        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Worked to listOf("take 3200,3200"), outcome to picker.steps)
    }

    @Test
    fun `an item further off is walked to, which approaches it`() {
        picker.view = PickUpView(atLocation = true, items = listOf(cowhideNear))

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Approached to listOf("walk to 3203,3200"), outcome to picker.steps)
    }

    @Test
    fun `nothing left lying ends the step`() {
        picker.view = PickUpView(atLocation = true, items = emptyList())

        assertEquals(StepOutcome.Finished, work.act(left = null))
    }

    @Test
    fun `a full bag ends the step, naming what it left lying`() {
        picker.view = PickUpView(atLocation = true, items = listOf(bonesUnderfoot.copy(fits = false), cowhideNear.copy(fits = false)))

        assertEquals(StepOutcome.BagFull(listOf(bones, cowhide)), work.act(left = null))
    }

    @Test
    fun `away from the area with nothing in reach the step walks there`() {
        picker.view = PickUpView(atLocation = false, items = emptyList())

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Approached to listOf("walk to location"), outcome to picker.steps)
    }

    @Test
    fun `still away from the area after walking there, the step ends instead of walking again`() {
        picker.view = PickUpView(atLocation = false, items = emptyList())
        work.act(left = null)

        val outcome = work.act(left = null)

        assertEquals(StepOutcome.Finished to listOf("walk to location"), outcome to picker.steps)
    }

    @Test
    fun `an item taken twice with nothing picked up is set aside for the next`() {
        picker.view = PickUpView(atLocation = true, items = listOf(bonesUnderfoot, cowhideNear))
        work.act(left = null)

        work.act(left = null)

        assertEquals(listOf("take 3200,3200", "walk to 3203,3200"), picker.steps)
    }

    @Test
    fun `an item walked to twice with nothing picked up is set aside, which leaves nothing to take`() {
        picker.view = PickUpView(atLocation = true, items = listOf(cowhideNear))
        work.act(left = null)

        assertEquals(StepOutcome.Finished, work.act(left = null))
    }

    @Test
    fun `an item set aside stays so for the rest of the turn`() {
        picker.view = PickUpView(atLocation = true, items = listOf(bonesUnderfoot, cowhideNear))
        work.act(left = null)
        work.act(left = null)
        picker.pickedUp = 1

        work.act(left = null)

        assertEquals(listOf("take 3200,3200", "walk to 3203,3200", "walk to 3203,3200"), picker.steps)
    }

    @Test
    fun `an item taken again after something was picked up is no retry`() {
        work.act(left = null)
        picker.pickedUp = 1

        work.act(left = null)

        assertEquals(listOf("take 3200,3200", "take 3200,3200"), picker.steps)
    }

    @Test
    fun `the same item walked to then taken is no retry`() {
        picker.view = PickUpView(atLocation = true, items = listOf(cowhideNear))
        work.act(left = null)
        picker.view = PickUpView(atLocation = true, items = listOf(cowhideNear.copy(distance = 0, usableFromHere = true)))

        work.act(left = null)

        assertEquals(listOf("walk to 3203,3200", "take 3203,3200"), picker.steps)
    }

    @Test
    fun `walking to the area then finding an item is no retry`() {
        picker.view = PickUpView(atLocation = false, items = emptyList())
        work.act(left = null)
        picker.view = PickUpView(atLocation = true, items = listOf(cowhideNear))

        work.act(left = null)

        assertEquals(listOf("walk to location", "walk to 3203,3200"), picker.steps)
    }
}
