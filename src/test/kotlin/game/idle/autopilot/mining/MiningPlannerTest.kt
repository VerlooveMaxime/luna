package game.idle.autopilot.mining

import game.idle.autopilot.mining.MiningDecision.Blocked
import game.idle.autopilot.mining.MiningDecision.Mine
import game.idle.autopilot.mining.MiningDecision.WalkTo
import game.idle.autopilot.mining.MiningDecision.WalkToLocation
import game.skill.mining.Ore
import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MiningPlannerTest {

    private val near = rock(3200, 3200, distance = 2)
    private val far = rock(3210, 3200, distance = 9)
    private val inReach = rock(3205, 3205, distance = 0, usableFromHere = true)

    /** Luna's ores name themselves from the item definitions, which need the cache. */
    @BeforeEach
    fun `item definitions are loaded`() {
        TestWorld.world
    }

    @Test
    fun `a rock in reach is mined before a nearer one out of reach`() {
        assertEquals(Mine(inReach), MiningPlanner.decide(miningView(listOf(near, inReach)), Ore.COPPER))
    }

    @Test
    fun `out of reach, the nearest rock is walked to`() {
        assertEquals(WalkTo(near), MiningPlanner.decide(miningView(listOf(far, near)), Ore.COPPER))
    }

    @Test
    fun `equally near rocks are picked west to east, then south to north`() {
        val north = rock(3200, 3205, distance = 2)

        assertEquals(WalkTo(near), MiningPlanner.decide(miningView(listOf(north, near)), Ore.COPPER))
    }

    @Test
    fun `without a pickaxe nothing is mined`() {
        assertEquals(Blocked(MiningBlockedReason.NO_PICKAXE), MiningPlanner.decide(miningView(listOf(inReach), hasUsablePickaxe = false), Ore.COPPER))
    }

    @Test
    fun `an ore above the player's level is refused`() {
        assertEquals(Blocked(MiningBlockedReason.LEVEL_TOO_LOW), MiningPlanner.decide(miningView(listOf(inReach)), Ore.IRON))
    }

    @Test
    fun `a full inventory blocks`() {
        assertEquals(Blocked(MiningBlockedReason.INVENTORY_FULL), MiningPlanner.decide(miningView(listOf(inReach), inventoryFull = true), Ore.COPPER))
    }

    @Test
    fun `with no rock in sight away from the work spot, the player walks back`() {
        assertEquals(WalkToLocation, MiningPlanner.decide(miningView(emptyList(), atLocation = false), Ore.COPPER))
    }

    @Test
    fun `with no rock at the work spot, the step blocks`() {
        assertEquals(Blocked(MiningBlockedReason.NO_ROCK), MiningPlanner.decide(miningView(emptyList()), Ore.COPPER))
    }
}
