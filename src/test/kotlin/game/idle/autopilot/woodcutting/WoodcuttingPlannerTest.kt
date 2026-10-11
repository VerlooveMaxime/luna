package game.idle.autopilot.woodcutting

import game.idle.autopilot.woodcutting.WoodcuttingDecision.Chop
import game.idle.autopilot.woodcutting.WoodcuttingDecision.Settled
import game.idle.autopilot.woodcutting.WoodcuttingDecision.WalkTo
import game.idle.autopilot.woodcutting.WoodcuttingDecision.WalkToLocation
import game.idle.flow.Aim
import game.idle.flow.Reason
import game.idle.flow.StepOutcome
import game.idle.flow.Target
import game.skill.woodcutting.cutTree.Tree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WoodcuttingPlannerTest {

    private val nextToPlayer = tree(3171, 3444, distance = 0, usableFromHere = true)
    private val acrossThePath = tree(3170, 3454, distance = 8)

    private val noTree = Settled(StepOutcome.Waiting(Reason.NoneInReach(Target.TREE)))

    private fun decide(view: WoodcuttingView, action: ChopAction = anyTree, skipped: Set<Aim> = emptySet()) =
        WoodcuttingPlanner.decide(view, action, skipped)

    @Test
    fun `an action needs at least one kind of tree`() {
        assertThrows<IllegalArgumentException> { ChopAction(emptySet()) }
    }

    @Test
    fun `no tree in sight at the location waits for one to grow back`() {
        val decision = decide(view(emptyList(), atLocation = true))

        assertEquals(noTree, decision)
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

        assertEquals(noTree, decision)
    }

    @Test
    fun `a tree set aside is left out for the next best`() {
        val decision = decide(view(listOf(nextToPlayer, acrossThePath)), skipped = setOf(nextToPlayer.aim))

        assertEquals(WalkTo(acrossThePath), decision)
    }

    @Test
    fun `with every tree in reach set aside the step blocks instead of waiting`() {
        val decision = decide(view(listOf(nextToPlayer)), skipped = setOf(nextToPlayer.aim))

        assertEquals(Settled(StepOutcome.Blocked(Reason.NoneInReach(Target.TREE))), decision)
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
    fun `the highest level tree wins over a nearer one`() {
        val oak = tree(3160, 3440, distance = 11, kind = Tree.OAK)

        val decision = decide(view(listOf(nextToPlayer, oak)))

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
