package game.idle.autopilot.woodcutting

import game.idle.movement.Footprint
import game.idle.movement.ReachTerrain
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.Direction
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TreeScanTest {

    private val anchor = Position(3165, 3445, 0)
    private val scan = TreeScan(anchor, radius = 15, maxWalk = 30)

    private val tree = StandingTree(TREE_OBJECT_ID, Position(3171, 3444, 0), size = 1, tree = Tree.NORMAL)
    private val oak = StandingTree(1281, Position(3160, 3440, 0), size = 2, tree = Tree.OAK)

    /**
     * Trees block their own tiles. A fence along x = 3168 from y = 3440 to 3448 stops steps onto it. Chopping needs
     * an orthogonally adjacent tile.
     */
    private class Grid(private val trees: List<StandingTree>, private val fence: Boolean = false) : ReachTerrain<StandingTree> {

        override fun canStep(from: Position, direction: Direction): Boolean = !isBlocked(from.translate(1, direction))

        fun isBlocked(tile: Position): Boolean =
            trees.any { Footprint(it.position, it.size, it.size).covers(tile) } || (fence && tile.x == 3168 && tile.y in 3440..3448)

        override fun reachedFrom(tile: Position, target: StandingTree): Boolean =
            Direction.NESW.any { Footprint(target.position, target.size, target.size).covers(tile.translate(1, it)) }

        private fun Footprint.covers(tile: Position): Boolean =
            tile.x in position.x until position.x + width && tile.y in position.y until position.y + height
    }

    @Test
    fun `standing inside the area counts as at the location`() {
        assertTrue(scan.atLocation(Position(3180, 3460, 0)))
    }

    @Test
    fun `standing outside the area does not`() {
        assertFalse(scan.atLocation(Position(3181, 3445, 0)))
        assertFalse(scan.atLocation(Position(3165, 3445, 1)))
    }

    @Test
    fun `a tree next to the player is usable from here`() {
        val here = Position(3170, 3444, 0)

        val candidates = scan.candidates(here, listOf(tree), Grid(listOf(tree)))

        assertEquals(listOf(TreeCandidate(TREE_OBJECT_ID, tree.position, Tree.NORMAL, 0, true, here)), candidates)
    }

    @Test
    fun `a tree across open ground is measured in steps to its nearest side`() {
        val here = Position(3165, 3444, 0)

        val candidates = scan.candidates(here, listOf(tree), Grid(listOf(tree)))

        assertEquals(listOf(TreeCandidate(TREE_OBJECT_ID, tree.position, Tree.NORMAL, 5, false, Position(3170, 3444, 0))), candidates)
    }

    @Test
    fun `a tile taken by another tree is no place to chop from`() {
        val here = Position(3165, 3444, 0)
        val neighbour = StandingTree(TREE_OBJECT_ID, Position(3170, 3444, 0), size = 1, tree = Tree.NORMAL)

        val candidates = scan.candidates(here, listOf(tree), Grid(listOf(tree, neighbour)))

        assertEquals(Position(3171, 3443, 0), candidates.single().approach)
    }

    @Test
    fun `a fence makes the walk longer than the straight line`() {
        val here = Position(3165, 3444, 0)

        val candidates = scan.candidates(here, listOf(tree), Grid(listOf(tree), fence = true))

        assertEquals(9, candidates.single().distance)
    }

    @Test
    fun `a two by two tree is approached at its footprint`() {
        val here = Position(3165, 3444, 0)

        val candidates = scan.candidates(here, listOf(oak), Grid(listOf(oak)))

        assertEquals(Position(3162, 3441, 0), candidates.single().approach)
        assertEquals(3, candidates.single().distance)
    }

    @Test
    fun `a tree with no reachable side is left out`() {
        val walledIn = StandingTree(TREE_OBJECT_ID, Position(3171, 3460, 0), size = 1, tree = Tree.NORMAL)

        val candidates = scan.candidates(Position(3165, 3444, 0), listOf(walledIn), Grid(listOf(walledIn), fence = false).walledOff())

        assertEquals(emptyList<TreeCandidate>(), candidates)
    }

    @Test
    fun `by default the scan reaches forty eight tiles from the anchor`() {
        val wide = TreeScan(anchor, radius = 15)

        assertEquals(1, wide.candidates(Position(3213, 3445, 0), listOf(tree), Grid(listOf(tree))).size)
        assertEquals(0, wide.candidates(Position(3214, 3445, 0), listOf(tree), Grid(listOf(tree))).size)
    }

    @Test
    fun `too far from the anchor nothing is scanned`() {
        val candidates = scan.candidates(Position(3196, 3445, 0), listOf(tree), Grid(listOf(tree)))

        assertEquals(emptyList<TreeCandidate>(), candidates)
    }

    @Test
    fun `on another floor nothing is scanned`() {
        val candidates = scan.candidates(Position(3165, 3445, 1), listOf(tree), Grid(listOf(tree)))

        assertEquals(emptyList<TreeCandidate>(), candidates)
    }

    @Test
    fun `just outside the area the scan still runs`() {
        val here = Position(3181, 3445, 0)

        val candidates = scan.candidates(here, listOf(tree), Grid(listOf(tree)))

        assertEquals(1, candidates.size)
    }

    /** Nothing can be stepped onto except the origin's own tile. */
    private fun ReachTerrain<StandingTree>.walledOff(): ReachTerrain<StandingTree> = object : ReachTerrain<StandingTree> by this {
        override fun canStep(from: Position, direction: Direction): Boolean = false
    }
}
