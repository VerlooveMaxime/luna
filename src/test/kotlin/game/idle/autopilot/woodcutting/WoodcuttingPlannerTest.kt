package game.idle.autopilot.woodcutting

import game.idle.autopilot.woodcutting.WoodcuttingDecision.Blocked
import game.idle.autopilot.woodcutting.WoodcuttingDecision.Chop
import game.idle.autopilot.woodcutting.WoodcuttingDecision.WalkTo
import game.idle.autopilot.woodcutting.WoodcuttingDecision.WalkToLocation
import game.skill.woodcutting.cutTree.Tree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WoodcuttingPlannerTest {

    private val nextToPlayer = tree(3171, 3444, distance = 0, usableFromHere = true)
    private val acrossThePath = tree(3170, 3454, distance = 8)

    private fun decide(view: WoodcuttingView, action: ChopAction = anyTree) = WoodcuttingPlanner.decide(view, action)

    @Test
    fun `an action needs at least one kind of tree`() {
        assertThrows<IllegalArgumentException> { ChopAction(emptySet()) }
    }

    @Test
    fun `a player without a usable axe is blocked even next to a tree`() {
        val decision = decide(view(listOf(nextToPlayer), hasUsableAxe = false))

        assertEquals(Blocked(BlockedReason.NO_AXE), decision)
    }

    @Test
    fun `a full inventory blocks the autopilot`() {
        val decision = decide(view(listOf(nextToPlayer), inventoryFull = true, logsInInventory = 5))

        assertEquals(Blocked(BlockedReason.INVENTORY_FULL), decision)
    }

    @Test
    fun `logs are kept while the inventory has room`() {
        val decision = decide(view(listOf(nextToPlayer), inventoryFull = false, logsInInventory = 27))

        assertEquals(Chop(nextToPlayer), decision)
    }

    @Test
    fun `no tree in sight at the location blocks the autopilot`() {
        val decision = decide(view(emptyList(), atLocation = true))

        assertEquals(Blocked(BlockedReason.NO_TREE), decision)
    }

    @Test
    fun `no tree in sight away from the location walks back to it`() {
        val decision = decide(view(emptyList(), atLocation = false))

        assertEquals(WalkToLocation, decision)
    }

    @Test
    fun `a tree in sight is preferred over walking back to the location`() {
        val decision = decide(view(listOf(acrossThePath), atLocation = false))

        assertEquals(WalkTo(acrossThePath), decision)
    }

    @Test
    fun `trees the player did not ask for are ignored`() {
        val decision = decide(view(listOf(nextToPlayer)), ChopAction(setOf(Tree.OAK)))

        assertEquals(Blocked(BlockedReason.NO_TREE), decision)
    }

    @Test
    fun `trees above the player's level are ignored`() {
        val oak = tree(3171, 3444, distance = 0, usableFromHere = true, kind = Tree.OAK)

        val decision = decide(view(listOf(oak), woodcuttingLevel = 14))

        assertEquals(Blocked(BlockedReason.NO_TREE), decision)
    }

    @Test
    fun `a tree at exactly the player's level can be cut`() {
        val oak = tree(3171, 3444, distance = 0, usableFromHere = true, kind = Tree.OAK)

        val decision = decide(view(listOf(oak), woodcuttingLevel = 15))

        assertEquals(Chop(oak), decision)
    }

    @Test
    fun `a tree usable from where the player stands is chopped`() {
        val decision = decide(view(listOf(nextToPlayer)))

        assertEquals(Chop(nextToPlayer), decision)
    }

    @Test
    fun `a tree out of reach is walked to`() {
        val decision = decide(view(listOf(acrossThePath)))

        assertEquals(WalkTo(acrossThePath), decision)
    }

    @Test
    fun `the highest level tree the player can cut wins over a nearer one`() {
        val oak = tree(3160, 3440, distance = 11, kind = Tree.OAK)

        val decision = decide(view(listOf(nextToPlayer, oak), woodcuttingLevel = 20))

        assertEquals(WalkTo(oak), decision)
    }

    @Test
    fun `a tree usable without moving wins over an equally near one`() {
        val diagonal = tree(3170, 3443, distance = 0, usableFromHere = false)

        val decision = decide(view(listOf(diagonal, nextToPlayer)))

        assertEquals(Chop(nextToPlayer), decision)
    }

    @Test
    fun `the nearest of equal trees is chosen`() {
        val nearer = tree(3168, 3437, distance = 3)

        val decision = decide(view(listOf(acrossThePath, nearer)))

        assertEquals(WalkTo(nearer), decision)
    }

    @Test
    fun `equally near trees are chosen west to east then south to north`() {
        val northWest = tree(3168, 3446, distance = 4)
        val southEast = tree(3169, 3434, distance = 4)
        val southWest = tree(3168, 3434, distance = 4)

        val decision = decide(view(listOf(northWest, southEast, southWest)))

        assertEquals(WalkTo(southWest), decision)
    }
}
