package game.idle.autopilot.cooking

import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CookingActivityTest {

    private val fire = place(3100, 3095, distance = 3)
    private val farFire = place(3106, 3095, distance = 7)
    private val fireInReach = fire.copy(distance = 0, usableFromHere = true)

    private fun place(x: Int, y: Int, distance: Int) =
        PlaceCandidate(objectId = 2732, position = Position(x, y), distance, usableFromHere = false, approach = Position(x - 1, y))

    private fun view(places: List<PlaceCandidate> = listOf(fire, farFire), rawSlot: Int? = 2, windowOpen: Boolean = false, atLocation: Boolean = true) =
        CookingView(rawSlot, windowOpen, atLocation, places)

    private val cooker = FakeCooker(view())
    private val activity = CookingActivity(cooker)

    @Test
    fun `raw food is used on a fire in reach`() {
        cooker.view = view(listOf(farFire, fireInReach))

        activity.act()

        assertEquals(listOf("use 2 on 3100,3095"), cooker.steps)
    }

    @Test
    fun `the nearest fire out of reach is walked to`() {
        activity.act()

        assertEquals(listOf("walk to 3100,3095"), cooker.steps)
    }

    @Test
    fun `places at the same distance are picked west to east, then south to north`() {
        cooker.view = view(listOf(place(3102, 3096, 3), place(3102, 3095, 3), place(3101, 3099, 3)))

        activity.act()

        assertEquals(listOf("walk to 3101,3099"), cooker.steps)
    }

    @Test
    fun `an open cooking window is answered with cook all`() {
        cooker.view = view(windowOpen = true)

        activity.act()

        assertEquals(listOf("cook all"), cooker.steps)
    }

    @Test
    fun `away from the work spot with no fire in sight the player walks back`() {
        cooker.view = view(emptyList(), atLocation = false)

        activity.act()

        assertEquals(listOf("walk to location"), cooker.steps)
    }

    @Test
    fun `at the work spot with no fire the step blocks once with a message`() {
        cooker.view = view(emptyList())

        activity.act()
        activity.act()

        assertEquals(listOf("tell ${CookingBlockedReason.NO_FIRE.message}"), cooker.steps)
        assertFalse(activity.isDone())
    }

    @Test
    fun `without an amount the step ends once no raw food is left`() {
        cooker.view = view(rawSlot = null)

        activity.act()

        assertTrue(activity.isDone())
    }

    @Test
    fun `with an amount the step stops cooking and ends once that many were used`() {
        val counting = CookingActivity(cooker, amount = 1)
        cooker.raw = 3
        cooker.view = view(windowOpen = true)
        counting.act()
        cooker.raw = 2

        counting.act()

        assertTrue(counting.isDone())
        assertEquals(listOf("cook all", "stop"), cooker.steps)
    }

    @Test
    fun `cooking that reaches the amount stops being busy so the step can stop it`() {
        val counting = CookingActivity(cooker, amount = 2)
        cooker.raw = 3
        counting.act()
        cooker.busy = true
        cooker.raw = 2

        assertTrue(counting.isBusy())

        cooker.raw = 1

        assertFalse(counting.isBusy())
    }

    @Test
    fun `a fire used twice in a row without the window opening is skipped`() {
        cooker.view = view(listOf(fireInReach, farFire))
        activity.act()

        activity.act()

        assertEquals(listOf("use 2 on 3100,3095", "walk to 3106,3095"), cooker.steps)
    }

    @Test
    fun `using one fire and then another is progress, not a retry`() {
        cooker.view = view(listOf(fireInReach))
        activity.act()
        cooker.view = view(listOf(farFire.copy(distance = 0, usableFromHere = true)))

        activity.act()

        assertEquals(listOf("use 2 on 3100,3095", "use 2 on 3106,3095"), cooker.steps)
    }

    @Test
    fun `an idle cooker is not busy`() {
        assertFalse(activity.isBusy())
    }

    @Test
    fun `before its first act a step with an amount follows the cooker's busy`() {
        cooker.busy = true

        assertTrue(CookingActivity(cooker, amount = 1).isBusy())
    }

    @Test
    fun `walking to a fire and then using it is progress, not a retry`() {
        activity.act()
        cooker.view = view(listOf(fireInReach, farFire))

        activity.act()

        assertEquals(listOf("walk to 3100,3095", "use 2 on 3100,3095"), cooker.steps)
    }
}
