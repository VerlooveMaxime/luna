package io.luna.game.model.path

import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.ArrayDeque

class FailoverPathfinderTest {

    private val origin = Position(3200, 3200)
    private val target = Position(3400, 3200)
    private val firstRoute = Position(3300, 3200)
    private val secondRoute = Position(3300, 3201)

    /** Answers every search with [type] and a route through [through], counting the searches. */
    private class Fake(
        private val supported: Boolean,
        private val type: PathResultType,
        private val through: Position,
    ) : GamePathfinder<Position>(null) {

        var searches = 0
            private set

        override fun supports(origin: Position, target: Position): Boolean = supported

        override fun find(origin: Position, target: Position): PathResult<Position> {
            searches++
            return PathResult(type, ArrayDeque(listOf(through)))
        }
    }

    private fun first(supported: Boolean = true, type: PathResultType = PathResultType.COMPLETE) =
        Fake(supported, type, firstRoute)

    private fun second(supported: Boolean = true) = Fake(supported, PathResultType.COMPLETE, secondRoute)

    private fun routeThrough(first: Fake, second: Fake): Position? =
        FailoverPathfinder(first, second).find(origin, target).path.peekFirst()

    @Test
    fun `a route the first pathfinder finds is taken`() {
        assertEquals(firstRoute, routeThrough(first(), second()))
    }

    @Test
    fun `a partial route of the first pathfinder is taken`() {
        assertEquals(firstRoute, routeThrough(first(type = PathResultType.PARTIAL), second()))
    }

    @Test
    fun `a search the first pathfinder finds no route for goes to the second`() {
        assertEquals(secondRoute, routeThrough(first(type = PathResultType.FAILED), second()))
    }

    @Test
    fun `a search the first pathfinder cannot do goes to the second`() {
        assertEquals(secondRoute, routeThrough(first(supported = false), second()))
    }

    @Test
    fun `the first pathfinder is not asked for a search it cannot do`() {
        val first = first(supported = false)

        routeThrough(first, second())

        assertEquals(0, first.searches)
    }

    @Test
    fun `it supports what the first pathfinder supports`() {
        assertTrue(FailoverPathfinder(first(), second(supported = false)).supports(origin, target))
    }

    @Test
    fun `it supports what only the second pathfinder supports`() {
        assertTrue(FailoverPathfinder(first(supported = false), second()).supports(origin, target))
    }

    @Test
    fun `it supports nothing that neither pathfinder supports`() {
        assertFalse(FailoverPathfinder(first(supported = false), second(supported = false)).supports(origin, target))
    }
}
