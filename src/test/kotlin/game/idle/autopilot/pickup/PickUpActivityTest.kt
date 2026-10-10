package game.idle.autopilot.pickup

import game.idle.flow.option.FakeNames
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PickUpActivityTest {

    private val bones = 526
    private val cowhide = 1739
    private val bonesUnderfoot = GroundLoot(bones, Position(3200, 3200), distance = 0, fits = true)
    private val cowhideNear = GroundLoot(cowhide, Position(3203, 3200), distance = 3, fits = true)
    private val picker = FakePicker(PickUpView(atLocation = true, items = listOf(bonesUnderfoot)))
    private val names = FakeNames(items = mapOf(bones to "Bones", cowhide to "Cowhide"))

    private fun activity(amount: Int? = null) = PickUpActivity(picker, amount, names)

    @Test
    fun `an idle player lets the step act`() {
        assertFalse(activity().isBusy())
    }

    @Test
    fun `walking to an item keeps the player busy`() {
        picker.busy = true

        assertTrue(activity().isBusy())
    }

    @Test
    fun `an item underfoot is taken`() {
        activity().act()

        assertEquals(listOf("take 3200,3200"), picker.steps)
    }

    @Test
    fun `an item further off is walked to`() {
        picker.view = PickUpView(atLocation = true, items = listOf(cowhideNear))

        activity().act()

        assertEquals(listOf("walk to 3203,3200"), picker.steps)
    }

    @Test
    fun `taking an item does not end the step`() {
        val activity = activity()

        activity.act()

        assertFalse(activity.isDone())
    }

    @Test
    fun `nothing left lying ends the step`() {
        picker.view = PickUpView(atLocation = true, items = emptyList())
        val activity = activity()

        activity.act()

        assertTrue(activity.isDone())
    }

    @Test
    fun `away from the area with nothing in reach the step walks there`() {
        picker.view = PickUpView(atLocation = false, items = emptyList())
        val activity = activity()

        activity.act()

        assertEquals(listOf("walk to location"), picker.steps)
    }

    @Test
    fun `walking to the area does not end the step`() {
        picker.view = PickUpView(atLocation = false, items = emptyList())
        val activity = activity()

        activity.act()

        assertFalse(activity.isDone())
    }

    @Test
    fun `still away from the area after walking there, the step ends`() {
        picker.view = PickUpView(atLocation = false, items = emptyList())
        val activity = activity()
        activity.act()

        activity.act()

        assertTrue(activity.isDone())
    }

    @Test
    fun `a full bag ends the step`() {
        picker.view = PickUpView(atLocation = true, items = listOf(bonesUnderfoot.copy(fits = false)))
        val activity = activity()

        activity.act()

        assertTrue(activity.isDone())
    }

    @Test
    fun `a full bag says what the step left lying`() {
        picker.view = PickUpView(atLocation = true, items = listOf(bonesUnderfoot.copy(fits = false), cowhideNear.copy(fits = false)))

        activity().act()

        assertEquals(listOf("Autopilot: your bag is full, the pick-up step left bones, cowhide lying."), picker.told)
    }

    @Test
    fun `reaching the count ends the step`() {
        picker.pickedUp = 3
        val activity = activity(amount = 3)

        activity.act()

        assertTrue(activity.isDone())
    }

    @Test
    fun `reaching the count takes nothing more`() {
        picker.pickedUp = 3

        activity(amount = 3).act()

        assertEquals(emptyList<String>(), picker.steps)
    }

    @Test
    fun `short of its count the step goes on taking`() {
        picker.pickedUp = 2

        activity(amount = 3).act()

        assertEquals(listOf("take 3200,3200"), picker.steps)
    }

    @Test
    fun `its amount done is the pickups since the step began`() {
        picker.pickedUp = 4

        assertEquals(4, activity().amountDone())
    }

    @Test
    fun `an item taken twice with nothing picked up is skipped for the next`() {
        picker.view = PickUpView(atLocation = true, items = listOf(bonesUnderfoot, cowhideNear))
        val activity = activity()
        activity.act()

        activity.act()

        assertEquals(listOf("take 3200,3200", "walk to 3203,3200"), picker.steps)
    }

    @Test
    fun `an item walked to twice with nothing picked up is skipped`() {
        picker.view = PickUpView(atLocation = true, items = listOf(cowhideNear))
        val activity = activity()
        activity.act()

        activity.act()

        assertTrue(activity.isDone())
    }

    @Test
    fun `a skipped item stays skipped for the rest of the step`() {
        picker.view = PickUpView(atLocation = true, items = listOf(bonesUnderfoot, cowhideNear))
        val activity = activity()
        activity.act()
        activity.act()
        picker.pickedUp = 1

        activity.act()

        assertEquals(listOf("take 3200,3200", "walk to 3203,3200", "walk to 3203,3200"), picker.steps)
    }

    @Test
    fun `an item taken again after something was picked up is no retry`() {
        val activity = activity()
        activity.act()
        picker.pickedUp = 1

        activity.act()

        assertEquals(listOf("take 3200,3200", "take 3200,3200"), picker.steps)
    }

    @Test
    fun `the same item walked to then taken is no retry`() {
        picker.view = PickUpView(atLocation = true, items = listOf(cowhideNear))
        val activity = activity()
        activity.act()
        picker.view = PickUpView(atLocation = true, items = listOf(cowhideNear.copy(distance = 0)))

        activity.act()

        assertEquals(listOf("walk to 3203,3200", "take 3203,3200"), picker.steps)
    }

    @Test
    fun `walking to the area then finding an item is no retry`() {
        picker.view = PickUpView(atLocation = false, items = emptyList())
        val activity = activity()
        activity.act()
        picker.view = PickUpView(atLocation = true, items = listOf(cowhideNear))

        activity.act()

        assertEquals(listOf("walk to location", "walk to 3203,3200"), picker.steps)
    }
}
