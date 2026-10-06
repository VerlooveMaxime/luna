package game.idle.autopilot.mining

import game.skill.mining.Ore
import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MiningActivityTest {

    private val near = rock(3201, 3200, distance = 2)
    private val other = rock(3205, 3200, distance = 5)
    private val inReach = near.copy(distance = 0, usableFromHere = true)

    private val miner = FakeMiner(miningView(listOf(inReach)))
    private val activity = MiningActivity(miner, Ore.COPPER)

    /** Luna's ores name themselves from the item definitions, which need the cache. */
    @BeforeEach
    fun `item definitions are loaded`() {
        TestWorld.world
    }

    @Test
    fun `a rock in reach is mined`() {
        activity.act()

        assertEquals(listOf("mine 3201,3200"), miner.steps)
    }

    @Test
    fun `mining is not done before the inventory fills`() {
        activity.act()

        assertFalse(activity.isDone())
    }

    @Test
    fun `mining is done once the inventory is full after mining`() {
        activity.act()
        miner.view = miningView(listOf(inReach), inventoryFull = true)

        activity.act()

        assertTrue(activity.isDone())
    }

    @Test
    fun `a step that starts with a full inventory blocks instead of ending`() {
        miner.view = miningView(listOf(inReach), inventoryFull = true)

        activity.act()

        assertFalse(activity.isDone())
        assertEquals(listOf("tell ${MiningBlockedReason.INVENTORY_FULL.message}"), miner.steps)
    }

    @Test
    fun `walking to a rock does not count as mining`() {
        miner.view = miningView(listOf(near))
        activity.act()
        miner.view = miningView(listOf(near), inventoryFull = true)

        activity.act()

        assertFalse(activity.isDone())
    }

    @Test
    fun `the same rock is mined again once it gave an ore`() {
        activity.act()
        miner.ores = 1

        activity.act()

        assertEquals(listOf("mine 3201,3200", "mine 3201,3200"), miner.steps)
    }

    @Test
    fun `a rock clicked twice without an ore is passed over for the next one`() {
        miner.view = miningView(listOf(inReach, other))
        activity.act()

        activity.act()

        assertEquals(listOf("mine 3201,3200", "walk to 3205,3200"), miner.steps)
    }

    @Test
    fun `a rock walked to and then in reach is mined`() {
        miner.view = miningView(listOf(near))
        activity.act()
        miner.view = miningView(listOf(inReach))

        activity.act()

        assertEquals(listOf("walk to 3201,3200", "mine 3201,3200"), miner.steps)
    }

    @Test
    fun `another rock mined right after one that gave nothing is not a retry`() {
        val otherInReach = other.copy(distance = 0, usableFromHere = true)
        activity.act()
        miner.view = miningView(listOf(otherInReach))

        activity.act()

        assertEquals(listOf("mine 3201,3200", "mine 3205,3200"), miner.steps)
    }

    @Test
    fun `a rock walked to twice without coming in reach is passed over`() {
        miner.view = miningView(listOf(near))
        activity.act()

        activity.act()

        assertEquals(listOf("walk to 3201,3200", "tell ${MiningBlockedReason.NO_ROCK.message}"), miner.steps)
    }

    @Test
    fun `a blocked step says why once, not on every look`() {
        miner.view = miningView(emptyList())
        activity.act()

        activity.act()

        assertEquals(listOf("tell ${MiningBlockedReason.NO_ROCK.message}"), miner.steps)
    }

    @Test
    fun `away from the work spot with no rock in sight, the player walks back`() {
        miner.view = miningView(emptyList(), atLocation = false)

        activity.act()

        assertEquals(listOf("walk to location"), miner.steps)
    }

    @Test
    fun `a step with an amount is not done before that many ores were mined`() {
        val counting = MiningActivity(miner, Ore.COPPER, amount = 2)
        counting.act()
        miner.ores = 1

        counting.act()

        assertFalse(counting.isDone())
    }

    @Test
    fun `a step with an amount stops the mining and ends once that many ores were mined`() {
        val counting = MiningActivity(miner, Ore.COPPER, amount = 2)
        counting.act()
        miner.ores = 2

        counting.act()

        assertTrue(counting.isDone())
        assertEquals(listOf("mine 3201,3200", "stop"), miner.steps)
    }

    @Test
    fun `an idle miner leaves the step free to act`() {
        assertFalse(activity.isBusy())
    }

    @Test
    fun `a busy miner keeps the step busy`() {
        miner.busy = true

        assertTrue(activity.isBusy())
    }

    @Test
    fun `a step whose amount is reached is not busy, so it can stop the mining`() {
        val counting = MiningActivity(miner, Ore.COPPER, amount = 1)
        counting.act()
        miner.busy = true
        miner.ores = 1

        assertFalse(counting.isBusy())
    }

    @Test
    fun `before its first look a step with an amount is busy while the miner is`() {
        miner.busy = true

        assertTrue(MiningActivity(miner, Ore.COPPER, amount = 1).isBusy())
    }
}
