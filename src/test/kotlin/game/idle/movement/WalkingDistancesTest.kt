package game.idle.movement

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
}
