package game.idle.autopilot.fishing

import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FishingActivityTest {

    private val near = spot(3100, 3092, distance = 2)
    private val far = spot(3104, 3092, distance = 6)
    private val inReach = near.copy(distance = 0, usableFromHere = true)

    private fun spot(x: Int, y: Int, distance: Int, usable: Boolean = false) =
        SpotCandidate(npcIndex = x, position = Position(x, y), distance, usable, approach = Position(x, y + 1))

    private fun view(spots: List<SpotCandidate> = listOf(near, far), full: Boolean = false, atLocation: Boolean = true) =
        FishingView(hasTool = true, hasLevel = true, hasBait = true, inventoryFull = full, atLocation = atLocation, spots = spots)

    private val fisher = FakeFisher(view())
    private val activity = FishingActivity(fisher)

    @Test
    fun `a spot in reach is fished`() {
        fisher.view = view(listOf(far, inReach))

        activity.act()

        assertEquals(listOf("fish 3100,3092"), fisher.steps)
    }

    @Test
    fun `the nearest spot out of reach is walked to`() {
        activity.act()

        assertEquals(listOf("walk to 3100,3092"), fisher.steps)
    }

    @Test
    fun `spots at the same distance are picked west to east, then south to north`() {
        fisher.view = view(listOf(spot(3102, 3093, 2), spot(3102, 3092, 2), spot(3101, 3095, 2)))

        activity.act()

        assertEquals(listOf("walk to 3101,3095"), fisher.steps)
    }

    @Test
    fun `away from the work spot with no spot in sight the player walks back`() {
        fisher.view = view(emptyList(), atLocation = false)

        activity.act()

        assertEquals(listOf("walk to location"), fisher.steps)
    }

    @Test
    fun `at the work spot with no spot in sight the step blocks`() {
        fisher.view = view(emptyList())

        activity.act()

        assertEquals(FishingBlockedReason.NO_SPOT.message, activity.blocked())
    }

    @Test
    fun `a block is reported with its reason`() {
        fisher.view = view().copy(hasTool = false)

        activity.act()

        assertEquals(FishingBlockedReason.NO_TOOL.message, activity.blocked())
    }

    @Test
    fun `a block clears on the next decision that is not one`() {
        fisher.view = view().copy(hasTool = false)
        activity.act()
        fisher.view = view()

        activity.act()

        assertNull(activity.blocked())
    }

    @Test
    fun `without the tool the step blocks once with a message`() {
        fisher.view = view().copy(hasTool = false)

        activity.act()
        activity.act()

        assertEquals(FishingBlockedReason.NO_TOOL.message, activity.blocked())
        assertFalse(activity.isDone())
    }

    @Test
    fun `below the method's level the step blocks with a message`() {
        fisher.view = view().copy(hasLevel = false)

        activity.act()

        assertEquals(FishingBlockedReason.LEVEL_TOO_LOW.message, activity.blocked())
    }

    @Test
    fun `without bait the step blocks once with a message and waits`() {
        fisher.view = view().copy(hasBait = false)

        activity.act()
        activity.act()

        assertEquals(FishingBlockedReason.NO_BAIT.message, activity.blocked())
        assertFalse(activity.isDone())
    }

    @Test
    fun `bait running out after a cast blocks the step instead of ending it`() {
        fisher.view = view(listOf(inReach))
        activity.act()
        fisher.view = view(listOf(inReach)).copy(hasBait = false)

        activity.act()

        assertEquals(FishingBlockedReason.NO_BAIT.message, activity.blocked())
        assertFalse(activity.isDone())
    }

    @Test
    fun `the tool is asked for before the level and the bait`() {
        fisher.view = view().copy(hasTool = false, hasLevel = false, hasBait = false)

        activity.act()

        assertEquals(FishingBlockedReason.NO_TOOL.message, activity.blocked())
    }

    @Test
    fun `the level is asked for before the bait`() {
        fisher.view = view().copy(hasLevel = false, hasBait = false)

        activity.act()

        assertEquals(FishingBlockedReason.LEVEL_TOO_LOW.message, activity.blocked())
    }

    @Test
    fun `a step that starts with a full inventory blocks instead of ending`() {
        fisher.view = view(full = true)

        activity.act()

        assertEquals(FishingBlockedReason.INVENTORY_FULL.message, activity.blocked())
        assertFalse(activity.isDone())
    }

    @Test
    fun `without an amount the step ends once the inventory fills after fishing`() {
        fisher.view = view(listOf(inReach))
        activity.act()
        fisher.view = view(listOf(inReach), full = true)

        activity.act()

        assertTrue(activity.isDone())
    }

    @Test
    fun `walking to a spot does not count as fishing`() {
        activity.act()
        fisher.view = view(full = true)

        activity.act()

        assertFalse(activity.isDone())
    }

    @Test
    fun `with an amount the step stops fishing and ends once that many were caught`() {
        val counting = FishingActivity(fisher, amount = 1)
        fisher.view = view(listOf(inReach))
        counting.act()
        fisher.catches = 1

        counting.act()

        assertTrue(counting.isDone())
        assertEquals(listOf("fish 3100,3092", "stop"), fisher.steps)
    }

    @Test
    fun `fishing that reaches the amount stops being busy so the step can stop it`() {
        val counting = FishingActivity(fisher, amount = 2)
        counting.act()
        fisher.busy = true
        fisher.catches = 1

        assertTrue(counting.isBusy())

        fisher.catches = 2

        assertFalse(counting.isBusy())
    }

    @Test
    fun `a spot fished twice in a row without the inventory filling is skipped`() {
        fisher.view = view(listOf(inReach, far))
        activity.act()

        activity.act()

        assertEquals(listOf("fish 3100,3092", "walk to 3104,3092"), fisher.steps)
    }

    @Test
    fun `fishing one spot and then another is progress, not a retry`() {
        fisher.view = view(listOf(inReach))
        activity.act()
        val other = spot(3104, 3092, distance = 0, usable = true)
        fisher.view = view(listOf(other))

        activity.act()

        assertEquals(listOf("fish 3100,3092", "fish 3104,3092"), fisher.steps)
    }

    @Test
    fun `an idle fisher is not busy`() {
        assertFalse(activity.isBusy())
    }

    @Test
    fun `before its first act a step with an amount follows the fisher's busy`() {
        fisher.busy = true

        assertTrue(FishingActivity(fisher, amount = 1).isBusy())
    }

    @Test
    fun `walking to a spot and then fishing it is progress, not a retry`() {
        activity.act()
        fisher.view = view(listOf(inReach, far))

        activity.act()

        assertEquals(listOf("walk to 3100,3092", "fish 3100,3092"), fisher.steps)
    }

    @Test
    fun `before its first act the step has done nothing`() {
        assertEquals(0, FishingActivity(fisher).amountDone())
    }

    @Test
    fun `its amount done is the fish caught since the step began`() {
        val counting = FishingActivity(fisher)
        fisher.view = view(listOf(inReach))
        counting.act()
        fisher.catches = 1

        assertEquals(1, counting.amountDone())
    }
}
