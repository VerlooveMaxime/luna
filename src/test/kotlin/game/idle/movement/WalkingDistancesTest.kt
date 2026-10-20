package game.idle.movement

import api.bot.webwalk.model.EdgeType
import io.luna.game.model.Direction
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WalkingDistancesTest {

    private val origin = Position(10, 10, 0)
    private val bounds = TileBounds.around(origin, 3)

    /** A fence along x = 12 from y = 8 to y = 12: nothing steps onto those tiles. */
    private fun fenced(from: Position, direction: Direction): Boolean {
        val next = from.translate(1, direction)
        return !(next.x == 12 && next.y in 8..12)
    }

    private fun doorway(x: Int) = Doorway(id = x, Position(x, 10, 0), EdgeType.DOOR)

    /** A corridor along y = 10, walked east or west; a door stands between x - 1 and x for each x of [doors], shut when true. */
    private fun corridor(doors: Map<Int, Boolean>): (Position, Direction) -> Step? = { from, direction ->
        val to = from.translate(1, direction)
        val shut = doors[maxOf(from.x, to.x)]
        when {
            to.y != 10 -> null
            shut != null -> DoorStep(doorway(maxOf(from.x, to.x)), from, to, shut)
            else -> Step.Plain
        }
    }

    /** A tunnel through a wall along x = 12, used from 11 10, landing on [to] in [ticks]. */
    private fun tunnel(to: Position = Position(13, 10, 0), from: Position = Position(11, 10, 0), ticks: Double = 2.0) =
        EntranceStep(Doorway(6912, Position(12, 10, 0), EdgeType.ENTRANCE), from, to, ticks)

    /** A walk that a wall along x = 12 stops at any y, through [entrances] too. */
    private fun walledThrough(vararg entrances: EntranceStep): Walk = object : Walk {
        override fun step(from: Position, direction: Direction): Step? = if (from.translate(1, direction).x != 12) Step.Plain else null

        override fun entrances(from: Position): List<EntranceStep> = entrances.filter { it.from == from }
    }

    @Test
    fun `the origin is zero steps away`() {
        val distances = WalkingDistances.from(origin, bounds) { _, _ -> true }

        assertEquals(0, distances[origin])
    }

    @Test
    fun `open ground is walked diagonally`() {
        val distances = WalkingDistances.from(origin, bounds) { _, _ -> true }

        assertEquals(3, distances[Position(13, 13, 0)])
    }

    @Test
    fun `a wall is walked around`() {
        val distances = WalkingDistances.from(origin, bounds, ::fenced)

        assertEquals(6, distances[Position(13, 10, 0)])
    }

    @Test
    fun `a tile nothing steps onto is unreachable`() {
        val distances = WalkingDistances.from(origin, bounds, ::fenced)

        assertNull(distances[Position(12, 10, 0)])
    }

    @Test
    fun `tiles outside the bounds are never visited`() {
        val distances = WalkingDistances.from(origin, bounds) { _, _ -> true }

        assertNull(distances[Position(14, 10, 0)])
        assertEquals(49, distances.reachable.size)
    }

    @Test
    fun `another floor is unreachable`() {
        val distances = WalkingDistances.from(origin, bounds) { _, _ -> true }

        assertNull(distances[Position(10, 10, 1)])
    }

    @Test
    fun `the origin must lie inside the bounds`() {
        assertThrows<IllegalArgumentException> { WalkingDistances.from(Position(0, 0, 0), bounds) { _, _ -> true } }
    }

    @Test
    fun `the first goal a walk reaches is the one fewest steps away, not the nearest in a straight line`() {
        val behindFence = Position(13, 10, 0)
        val inTheOpen = Position(10, 15, 0)

        val reached = WalkingDistances.firstReached(origin, setOf(behindFence, inTheOpen), maxSteps = 10, ::fenced)

        assertEquals(inTheOpen, reached)
    }

    @Test
    fun `a goal more steps away than the limit is not reached, even inside the limit in a straight line`() {
        val reached = WalkingDistances.firstReached(origin, setOf(Position(13, 10, 0)), maxSteps = 5, ::fenced)

        assertNull(reached)
    }

    @Test
    fun `a goal at the step limit is reached`() {
        val reached = WalkingDistances.firstReached(origin, setOf(Position(13, 10, 0)), maxSteps = 6, ::fenced)

        assertEquals(Position(13, 10, 0), reached)
    }

    @Test
    fun `a shut door on the way costs six steps more`() {
        val distances = WalkingDistances.through(origin, bounds, corridor(mapOf(12 to true)))

        assertEquals(9, distances[Position(13, 10, 0)])
    }

    @Test
    fun `an open door costs a step like any other`() {
        val distances = WalkingDistances.through(origin, bounds, corridor(mapOf(12 to false)))

        assertEquals(3, distances[Position(13, 10, 0)])
    }

    @Test
    fun `a walk through a shut door names it, with the tiles it is crossed between`() {
        val distances = WalkingDistances.through(origin, bounds, corridor(mapOf(12 to true)))

        val door = DoorStep(doorway(12), from = Position(11, 10, 0), to = Position(12, 10, 0), shut = true)
        assertEquals(listOf(door), distances.crossingsOn(Position(13, 10, 0)))
    }

    @Test
    fun `a walk through no door names none`() {
        val distances = WalkingDistances.through(origin, bounds, corridor(mapOf(12 to true)))

        assertEquals(emptyList<DoorStep>(), distances.crossingsOn(Position(9, 10, 0)))
    }

    @Test
    fun `the doors on the way are named in the order the walk goes through them`() {
        val distances = WalkingDistances.through(origin, bounds, corridor(mapOf(11 to false, 12 to true)))

        assertEquals(listOf(doorway(11), doorway(12)), distances.crossingsOn(Position(13, 10, 0)).map { it.doorway })
    }

    @Test
    fun `a walk round a shut door beats opening it, and names no door`() {
        val door = DoorStep(doorway(11), origin, Position(11, 10, 0), shut = true)
        val distances = WalkingDistances.through(origin, bounds, Walk { from, direction ->
            if (from == origin && direction == Direction.EAST) door else Step.Plain
        })

        assertEquals(2 to emptyList<DoorStep>(), distances[Position(11, 10, 0)] to distances.crossingsOn(Position(11, 10, 0)))
    }

    @Test
    fun `a dearer way through a shut door onto a tile already reached keeps the cheaper count`() {
        val onto = Position(11, 10, 0)
        val distances = WalkingDistances.through(origin, bounds, Walk { from, direction ->
            if (from.translate(1, direction) == onto) DoorStep(doorway(11), from, onto, shut = true) else Step.Plain
        })

        assertEquals(7, distances[onto])
    }

    @Test
    fun `a walk through doors must start inside its bounds too`() {
        assertThrows<IllegalArgumentException> { WalkingDistances.through(Position(0, 0, 0), bounds, Walk { _, _ -> Step.Plain }) }
    }

    @Test
    fun `bounds around a centre span the radius on both sides`() {
        assertEquals(TileBounds(7, 7, 13, 13, 0), bounds)
    }

    @Test
    fun `bounds contain their corners and not the tile past them`() {
        assertTrue(Position(7, 13, 0) in bounds)
        assertTrue(Position(13, 7, 0) in bounds)
        assertFalse(Position(6, 13, 0) in bounds)
        assertFalse(Position(14, 13, 0) in bounds)
        assertFalse(Position(7, 6, 0) in bounds)
        assertFalse(Position(7, 14, 0) in bounds)
    }

    @Test
    fun `bounds grow to include a tile`() {
        assertEquals(TileBounds(2, 7, 13, 20, 0), bounds.including(Position(2, 20, 0)))
    }

    @Test
    fun `bounds already holding a tile stay the same`() {
        assertEquals(bounds, bounds.including(origin))
    }

    @Test
    fun `bounds cannot include a tile on another floor`() {
        assertThrows<IllegalArgumentException> { bounds.including(Position(10, 10, 1)) }
    }

    @Test
    fun `a negative radius is rejected`() {
        assertThrows<IllegalArgumentException> { TileBounds.around(origin, -1) }
    }

    @Test
    fun `bounds ending west of their start are rejected`() {
        assertThrows<IllegalArgumentException> { TileBounds(5, 5, 4, 5, 0) }
    }

    @Test
    fun `bounds ending south of their start are rejected`() {
        assertThrows<IllegalArgumentException> { TileBounds(5, 5, 5, 4, 0) }
    }

    @Test
    fun `an entrance takes the walk past a wall for its ticks at a run`() {
        val distances = WalkingDistances.through(origin, bounds, walledThrough(tunnel()))

        assertEquals(5, distances[Position(13, 10, 0)])
    }

    @Test
    fun `a walk on from an entrance names it`() {
        val distances = WalkingDistances.through(origin, bounds, walledThrough(tunnel()))

        assertEquals(listOf(tunnel()), distances.crossingsOn(Position(13, 11, 0)))
    }

    @Test
    fun `an entrance landing outside the bounds is not taken`() {
        val distances = WalkingDistances.through(origin, bounds, walledThrough(tunnel(to = Position(14, 10, 0))))

        assertNull(distances[Position(14, 10, 0)])
    }

    @Test
    fun `an entrance may land past the bounds where its landings allow`() {
        val distances = WalkingDistances.through(origin, bounds, walledThrough(tunnel(to = Position(15, 10, 0))), landings = TileBounds.around(origin, 6))

        assertEquals(5, distances[Position(15, 10, 0)])
    }

    @Test
    fun `the walk goes on from a landing past the bounds only into them`() {
        val distances = WalkingDistances.through(origin, bounds, walledThrough(tunnel(to = Position(14, 10, 0))), landings = TileBounds.around(origin, 6))

        assertEquals(listOf(6, null), listOf(distances[Position(13, 10, 0)], distances[Position(15, 10, 0)]))
    }

    @Test
    fun `no entrance is used from a landing past the bounds`() {
        val onwards = tunnel(from = Position(15, 10, 0), to = Position(16, 10, 0))
        val distances = WalkingDistances.through(origin, bounds, walledThrough(tunnel(to = Position(15, 10, 0)), onwards), landings = TileBounds.around(origin, 6))

        assertNull(distances[Position(16, 10, 0)])
    }
}
