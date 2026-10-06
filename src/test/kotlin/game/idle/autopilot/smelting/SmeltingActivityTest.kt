package game.idle.autopilot.smelting

import game.skill.smithing.BarType
import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SmeltingActivityTest {

    private val near = furnace(3201, 3200, distance = 2)
    private val far = furnace(3210, 3200, distance = 9)
    private val inReach = near.copy(distance = 0, usableFromHere = true)

    private val smelter = FakeSmelter(smeltingView(listOf(inReach)))
    private val activity = SmeltingActivity(smelter, BarType.BRONZE)

    /** Luna's bars name their ores from the item definitions, which need the cache. */
    @BeforeEach
    fun `item definitions are loaded`() {
        TestWorld.world
    }

    @Test
    fun `the ore is used on a furnace in reach`() {
        activity.act()

        assertEquals(listOf("smelt 0,3201,3200"), smelter.steps)
    }

    @Test
    fun `a furnace in reach comes before a nearer one out of reach`() {
        smelter.view = smeltingView(listOf(near.copy(distance = 1), inReach.copy(position = far.position)))

        activity.act()

        assertEquals(listOf("smelt 0,3210,3200"), smelter.steps)
    }

    @Test
    fun `out of reach, the nearest furnace is walked to`() {
        smelter.view = smeltingView(listOf(far, near))

        activity.act()

        assertEquals(listOf("walk to 3201,3200"), smelter.steps)
    }

    @Test
    fun `equally near furnaces are picked west to east, then south to north`() {
        smelter.view = smeltingView(listOf(furnace(3201, 3205, distance = 2), near))

        activity.act()

        assertEquals(listOf("walk to 3201,3200"), smelter.steps)
    }

    @Test
    fun `with no ore from the start the step waits and says why`() {
        smelter.view = smeltingView(listOf(inReach), oreSlot = null)

        activity.act()

        assertFalse(activity.isDone())
        assertEquals(listOf("tell ${SmeltingBlockedReason.NOTHING_TO_SMELT.message}"), smelter.steps)
    }

    @Test
    fun `the step is done once the ore runs out after smelting some`() {
        activity.act()
        smelter.bars = 3
        smelter.view = smeltingView(listOf(inReach), oreSlot = null)

        activity.act()

        assertTrue(activity.isDone())
    }

    @Test
    fun `a bar above the player's level is refused`() {
        val steel = SmeltingActivity(smelter, BarType.STEEL)

        steel.act()

        assertEquals(listOf("tell ${SmeltingBlockedReason.LEVEL_TOO_LOW.message}"), smelter.steps)
    }

    @Test
    fun `with no furnace in sight away from the work spot, the player walks back`() {
        smelter.view = smeltingView(emptyList(), atLocation = false)

        activity.act()

        assertEquals(listOf("walk to location"), smelter.steps)
    }

    @Test
    fun `with no furnace at the work spot the step says why once`() {
        smelter.view = smeltingView(emptyList())
        activity.act()

        activity.act()

        assertEquals(listOf("tell ${SmeltingBlockedReason.NO_FURNACE.message}"), smelter.steps)
    }

    @Test
    fun `a furnace used twice without a bar is passed over`() {
        smelter.view = smeltingView(listOf(inReach, far))
        activity.act()

        activity.act()

        assertEquals(listOf("smelt 0,3201,3200", "walk to 3210,3200"), smelter.steps)
    }

    @Test
    fun `a furnace used again after it made a bar is not passed over`() {
        activity.act()
        smelter.bars = 1

        activity.act()

        assertEquals(listOf("smelt 0,3201,3200", "smelt 0,3201,3200"), smelter.steps)
    }

    @Test
    fun `a furnace walked to and then in reach is used`() {
        smelter.view = smeltingView(listOf(near))
        activity.act()
        smelter.view = smeltingView(listOf(inReach))

        activity.act()

        assertEquals(listOf("walk to 3201,3200", "smelt 0,3201,3200"), smelter.steps)
    }

    @Test
    fun `another furnace right after one that made nothing is not a retry`() {
        activity.act()
        smelter.view = smeltingView(listOf(inReach.copy(position = far.position)))

        activity.act()

        assertEquals(listOf("smelt 0,3201,3200", "smelt 0,3210,3200"), smelter.steps)
    }

    @Test
    fun `a step with an amount stops the smelting and ends once that many bars were made`() {
        val counting = SmeltingActivity(smelter, BarType.BRONZE, amount = 2)
        counting.act()
        smelter.bars = 2

        counting.act()

        assertTrue(counting.isDone())
        assertEquals(listOf("smelt 0,3201,3200", "stop"), smelter.steps)
    }

    @Test
    fun `a step with an amount is not done before that many bars were made`() {
        val counting = SmeltingActivity(smelter, BarType.BRONZE, amount = 2)
        counting.act()
        smelter.bars = 1

        counting.act()

        assertFalse(counting.isDone())
    }

    @Test
    fun `an idle smelter leaves the step free to act`() {
        assertFalse(activity.isBusy())
    }

    @Test
    fun `a busy smelter keeps the step busy`() {
        smelter.busy = true

        assertTrue(activity.isBusy())
    }

    @Test
    fun `a step whose amount is reached is not busy, so it can stop the smelting`() {
        val counting = SmeltingActivity(smelter, BarType.BRONZE, amount = 1)
        counting.act()
        smelter.busy = true
        smelter.bars = 1

        assertFalse(counting.isBusy())
    }

    @Test
    fun `before its first look a step with an amount is busy while the smelter is`() {
        smelter.busy = true

        assertTrue(SmeltingActivity(smelter, BarType.BRONZE, amount = 1).isBusy())
    }
}
