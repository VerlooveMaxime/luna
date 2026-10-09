package game.idle.autopilot.woodcutting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WoodcuttingActivityTest {

    private val nearTree = tree(3171, 3444, distance = 2)
    private val farTree = tree(3170, 3454, distance = 8)
    private val nearTreeInReach = nearTree.copy(distance = 1, usableFromHere = true)

    private val woodcutter = FakeWoodcutter(view(listOf(nearTree, farTree)))
    private val activity = WoodcuttingActivity(woodcutter, anyTree)

    @Test
    fun `chopping is not done before the inventory fills`() {
        woodcutter.view = view(listOf(nearTreeInReach))
        activity.act()

        assertFalse(activity.isDone())
    }

    @Test
    fun `chopping is done once the inventory is full after a chop`() {
        woodcutter.view = view(listOf(nearTreeInReach))
        activity.act()
        woodcutter.view = view(listOf(nearTreeInReach), inventoryFull = true, logsInInventory = 28)

        activity.act()

        assertTrue(activity.isDone())
        assertEquals(listOf("chop 3171,3444"), woodcutter.steps)
    }

    @Test
    fun `a step that starts with a full inventory blocks instead of ending`() {
        woodcutter.view = view(listOf(nearTreeInReach), inventoryFull = true, logsInInventory = 28)

        activity.act()

        assertFalse(activity.isDone())
        assertEquals(BlockedReason.INVENTORY_FULL.message, activity.blocked())
    }

    @Test
    fun `walking to a tree does not count as chopping`() {
        activity.act()
        woodcutter.view = view(listOf(nearTree), inventoryFull = true, logsInInventory = 28)

        activity.act()

        assertFalse(activity.isDone())
        assertEquals(listOf("walk to 3171,3444"), woodcutter.steps)
    }

    @Test
    fun `a step with an amount is not done before that many logs were cut`() {
        val counting = WoodcuttingActivity(woodcutter, anyTree, amount = 2)
        woodcutter.logs = 3
        woodcutter.view = view(listOf(nearTreeInReach))
        counting.act()
        woodcutter.logs = 4

        counting.act()

        assertFalse(counting.isDone())
    }

    @Test
    fun `a step with an amount stops chopping and ends once that many logs were cut`() {
        val counting = WoodcuttingActivity(woodcutter, anyTree, amount = 2)
        woodcutter.logs = 3
        woodcutter.view = view(listOf(nearTreeInReach))
        counting.act()
        woodcutter.logs = 5

        counting.act()

        assertTrue(counting.isDone())
        assertEquals(listOf("chop 3171,3444", "stop"), woodcutter.steps)
    }

    @Test
    fun `a chop that reaches the amount stops being busy so the step can stop it`() {
        val counting = WoodcuttingActivity(woodcutter, anyTree, amount = 1)
        woodcutter.view = view(listOf(nearTreeInReach))
        counting.act()
        woodcutter.busy = true

        woodcutter.logs = 1

        assertFalse(counting.isBusy())
    }

    @Test
    fun `an idle woodcutter is not busy`() {
        assertFalse(activity.isBusy())
    }

    @Test
    fun `before its first act a step with an amount follows the woodcutter's busy`() {
        val counting = WoodcuttingActivity(woodcutter, anyTree, amount = 1)
        woodcutter.busy = true

        assertTrue(counting.isBusy())
    }

    @Test
    fun `a step with an amount still ends when the inventory fills first`() {
        val counting = WoodcuttingActivity(woodcutter, anyTree, amount = 10)
        woodcutter.view = view(listOf(nearTreeInReach))
        counting.act()
        woodcutter.view = view(listOf(nearTreeInReach), inventoryFull = true, logsInInventory = 28)

        counting.act()

        assertTrue(counting.isDone())
    }

    @Test
    fun `the player is busy while the woodcutter is`() {
        woodcutter.busy = true

        assertTrue(activity.isBusy())
    }

    @Test
    fun `a tree in reach is chopped`() {
        woodcutter.view = view(listOf(nearTreeInReach))

        activity.act()

        assertEquals(listOf("chop 3171,3444"), woodcutter.steps)
    }

    @Test
    fun `a tree out of reach is walked to`() {
        activity.act()

        assertEquals(listOf("walk to 3171,3444"), woodcutter.steps)
    }

    @Test
    fun `away from the location with nothing in sight the player walks back`() {
        woodcutter.view = view(emptyList(), atLocation = false)

        activity.act()

        assertEquals(listOf("walk to location"), woodcutter.steps)
    }

    @Test
    fun `a block is reported with its reason`() {
        woodcutter.view = view(listOf(nearTree), hasUsableAxe = false)

        activity.act()

        assertEquals(BlockedReason.NO_AXE.message, activity.blocked())
    }

    @Test
    fun `a block clears on the next decision that is not one`() {
        woodcutter.view = view(listOf(nearTreeInReach), hasUsableAxe = false)
        activity.act()
        woodcutter.view = view(listOf(nearTreeInReach), hasUsableAxe = true)

        activity.act()

        assertNull(activity.blocked())
    }

    @Test
    fun `chopping moves on to the next tree once the last one falls`() {
        val farTreeInReach = farTree.copy(usableFromHere = true)
        woodcutter.view = view(listOf(nearTreeInReach, farTreeInReach))
        activity.act()
        woodcutter.view = view(listOf(farTreeInReach))

        activity.act()

        assertEquals(listOf("chop 3171,3444", "chop 3170,3454"), woodcutter.steps)
    }

    @Test
    fun `walking to a tree and then chopping it is progress, not a retry`() {
        activity.act()
        woodcutter.view = view(listOf(nearTreeInReach, farTree))

        activity.act()

        assertEquals(listOf("walk to 3171,3444", "chop 3171,3444"), woodcutter.steps)
    }

    @Test
    fun `a tree still out of reach after walking to it is skipped for the next best`() {
        activity.act()

        activity.act()

        assertEquals(listOf("walk to 3171,3444", "walk to 3170,3454"), woodcutter.steps)
    }

    @Test
    fun `a tree chopped twice in a row is skipped for the next best`() {
        woodcutter.view = view(listOf(nearTreeInReach, farTree))
        activity.act()

        activity.act()

        assertEquals(listOf("chop 3171,3444", "walk to 3170,3454"), woodcutter.steps)
    }

    @Test
    fun `a skipped tree stays skipped after other steps`() {
        activity.act()
        activity.act()
        woodcutter.view = view(listOf(nearTree, farTree), hasUsableAxe = false)
        activity.act()
        woodcutter.view = view(listOf(nearTree, farTree))

        activity.act()

        assertEquals(
            listOf("walk to 3171,3444", "walk to 3170,3454", "walk to 3170,3454"),
            woodcutter.steps,
        )
    }

    @Test
    fun `a lone tree that cannot be reached leaves no tree to cut`() {
        woodcutter.view = view(listOf(nearTree))
        activity.act()

        activity.act()

        assertEquals(BlockedReason.NO_TREE.message, activity.blocked())
    }
}
