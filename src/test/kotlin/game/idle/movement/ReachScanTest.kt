package game.idle.movement

import api.bot.webwalk.model.EdgeType
import io.luna.game.model.Direction
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReachScanTest {

    /** Something standing in the world, [size] tiles a side. */
    private data class Standing(val position: Position, val size: Int)

    private val anchor = Position(3165, 3445, 0)
    private val scan = ReachScan(anchor, radius = 15, maxWalk = 30)

    private val tree = Standing(Position(3171, 3444, 0), size = 1)
    private val oak = Standing(Position(3160, 3440, 0), size = 2)

    /** A shut door from 3167 3444 onto the fence's tile east of it. */
    private val door = DoorStep(Doorway(1530, Position(3168, 3444, 0), EdgeType.DOOR), from = Position(3167, 3444, 0), shut = true)

    /**
     * Targets block their own tiles. A fence along x = 3168 from y = 3440 to 3448 stops steps onto it; a [wall] there
     * stops them at any y. A target is used from an orthogonally adjacent tile. A [door] lets a walk through the fence.
     */
    private class Grid(
        private val standing: List<Standing>,
        private val fence: Boolean = false,
        private val wall: Boolean = false,
        private val door: DoorStep? = null,
    ) : ReachTerrain<Standing> {

        override fun step(from: Position, direction: Direction): Step? {
            val to = from.translate(1, direction)
            return when {
                door != null && from == door.from && to == door.doorway.position -> door
                isBlocked(to) -> null
                else -> Step.Plain
            }
        }

        fun isBlocked(tile: Position): Boolean =
            standing.any { it.covers(tile) } || (fence && tile.x == 3168 && tile.y in 3440..3448) || (wall && tile.x == 3168)

        override fun reachedFrom(tile: Position, target: Standing): Boolean =
            Direction.NESW.any { target.covers(tile.translate(1, it)) }

        private fun Standing.covers(tile: Position): Boolean =
            tile.x in position.x until position.x + size && tile.y in position.y until position.y + size
    }

    private fun reachable(here: Position, targets: List<Standing>, grid: ReachTerrain<Standing>, scan: ReachScan = this.scan): List<Pair<Standing, Reach>> =
        scan.reachable(here, targets, { Footprint(it.position, it.size, it.size) }, grid)

    @Test
    fun `standing inside the area counts as at the location`() {
        assertTrue(scan.atLocation(Position(3180, 3460, 0)))
    }

    @Test
    fun `standing outside the area does not`() {
        assertFalse(scan.atLocation(Position(3181, 3445, 0)))
    }

    @Test
    fun `standing on another floor does not`() {
        assertFalse(scan.atLocation(Position(3165, 3445, 1)))
    }

    @Test
    fun `a target next to the player is usable from here`() {
        val here = Position(3170, 3444, 0)

        val found = reachable(here, listOf(tree), Grid(listOf(tree)))

        assertEquals(listOf(tree to Reach(distance = 0, usableFromHere = true, approach = here)), found)
    }

    @Test
    fun `a target across open ground is measured in steps to its nearest side`() {
        val found = reachable(Position(3165, 3444, 0), listOf(tree), Grid(listOf(tree)))

        assertEquals(listOf(tree to Reach(distance = 5, usableFromHere = false, approach = Position(3170, 3444, 0))), found)
    }

    @Test
    fun `a tile taken by another target is no place to use it from`() {
        val neighbour = Standing(Position(3170, 3444, 0), size = 1)

        val found = reachable(Position(3165, 3444, 0), listOf(tree), Grid(listOf(tree, neighbour)))

        assertEquals(Position(3171, 3443, 0), found.single().second.approach)
    }

    @Test
    fun `a fence makes the walk longer than the straight line`() {
        val found = reachable(Position(3165, 3444, 0), listOf(tree), Grid(listOf(tree), fence = true))

        assertEquals(9, found.single().second.distance)
    }

    @Test
    fun `a target behind a shut door is walked to through it, the door counted and named`() {
        val found = reachable(Position(3165, 3444, 0), listOf(tree), Grid(listOf(tree), wall = true, door = door))

        assertEquals(Reach(distance = 11, usableFromHere = false, approach = Position(3170, 3444, 0), door), found.single().second)
    }

    @Test
    fun `a two by two target is approached at its footprint`() {
        val found = reachable(Position(3165, 3444, 0), listOf(oak), Grid(listOf(oak)))

        assertEquals(Reach(distance = 3, usableFromHere = false, approach = Position(3162, 3441, 0)), found.single().second)
    }

    @Test
    fun `a target with no reachable side is left out`() {
        val walledIn = Standing(Position(3171, 3460, 0), size = 1)

        val found = reachable(Position(3165, 3444, 0), listOf(walledIn), Grid(listOf(walledIn)).walledOff())

        assertEquals(emptyList<Pair<Standing, Reach>>(), found)
    }

    @Test
    fun `by default the scan reaches forty eight tiles from the anchor`() {
        val wide = ReachScan(anchor, radius = 15)

        assertEquals(1, reachable(Position(3213, 3445, 0), listOf(tree), Grid(listOf(tree)), wide).size)
    }

    @Test
    fun `by default the scan reaches no farther than forty eight tiles`() {
        val wide = ReachScan(anchor, radius = 15)

        assertEquals(0, reachable(Position(3214, 3445, 0), listOf(tree), Grid(listOf(tree)), wide).size)
    }

    @Test
    fun `too far from the anchor nothing is scanned`() {
        assertEquals(emptyList<Pair<Standing, Reach>>(), reachable(Position(3196, 3445, 0), listOf(tree), Grid(listOf(tree))))
    }

    @Test
    fun `on another floor nothing is scanned`() {
        assertEquals(emptyList<Pair<Standing, Reach>>(), reachable(Position(3165, 3445, 1), listOf(tree), Grid(listOf(tree))))
    }

    @Test
    fun `just outside the area the scan still runs`() {
        assertEquals(1, reachable(Position(3181, 3445, 0), listOf(tree), Grid(listOf(tree))).size)
    }

    @Test
    fun `the distances from a tile near the area count the steps into it`() {
        val distances = scan.distancesFrom(Position(3181, 3445, 0)) { _, _ -> Step.Plain }

        assertEquals(16, distances?.get(anchor))
    }

    @Test
    fun `too far from the anchor there are no distances`() {
        assertNull(scan.distancesFrom(Position(3196, 3445, 0)) { _, _ -> Step.Plain })
    }

    @Test
    fun `on another floor there are no distances`() {
        assertNull(scan.distancesFrom(Position(3165, 3445, 1)) { _, _ -> Step.Plain })
    }

    /** Nothing can be stepped onto except the origin's own tile. */
    private fun ReachTerrain<Standing>.walledOff(): ReachTerrain<Standing> = object : ReachTerrain<Standing> by this {
        override fun step(from: Position, direction: Direction): Step? = null
    }
}
