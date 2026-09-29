package game.idle.autopilot.woodcutting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WoodcuttingActivityTest {

    private val nearTree = tree(3171, 3444, distance = 2)
    private val farTree = tree(3170, 3454, distance = 8)
    private val nearTreeInReach = nearTree.copy(distance = 1, usableFromHere = true)

    private val woodcutter = FakeWoodcutter(view(listOf(nearTree, farTree)))
    private val activity = WoodcuttingActivity(woodcutter)

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
    fun `a full inventory of logs is dropped`() {
        woodcutter.view = view(listOf(nearTreeInReach), inventoryFull = true, logsInInventory = 28)

        activity.act()

        assertEquals(listOf("drop logs"), woodcutter.steps)
    }

    @Test
    fun `a blocked autopilot tells the player why`() {
        woodcutter.view = view(listOf(nearTree), hasUsableAxe = false)

        activity.act()

        assertEquals(listOf("tell ${BlockedReason.NO_AXE.message}"), woodcutter.steps)
    }

    @Test
    fun `the same reason is told only once`() {
        woodcutter.view = view(listOf(nearTree), hasUsableAxe = false)

        activity.act()
        activity.act()

        assertEquals(listOf("tell ${BlockedReason.NO_AXE.message}"), woodcutter.steps)
    }

    @Test
    fun `the reason is told again once the autopilot has moved on in between`() {
        woodcutter.view = view(listOf(nearTreeInReach), hasUsableAxe = false)
        activity.act()
        woodcutter.view = view(listOf(nearTreeInReach), hasUsableAxe = true)
        activity.act()
        woodcutter.view = view(listOf(nearTreeInReach), hasUsableAxe = false)

        activity.act()

        val noAxe = "tell ${BlockedReason.NO_AXE.message}"
        assertEquals(listOf(noAxe, "chop 3171,3444", noAxe), woodcutter.steps)
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
        woodcutter.view = view(listOf(nearTree, farTree), inventoryFull = true, logsInInventory = 28)
        activity.act()
        woodcutter.view = view(listOf(nearTree, farTree))

        activity.act()

        assertEquals(
            listOf("walk to 3171,3444", "walk to 3170,3454", "drop logs", "walk to 3170,3454"),
            woodcutter.steps,
        )
    }

    @Test
    fun `a lone tree that cannot be reached leaves no tree to cut`() {
        woodcutter.view = view(listOf(nearTree))
        activity.act()

        activity.act()

        assertEquals(listOf("walk to 3171,3444", "tell ${BlockedReason.NO_TREE.message}"), woodcutter.steps)
    }
}
