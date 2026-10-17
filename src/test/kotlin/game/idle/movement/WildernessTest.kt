package game.idle.movement

import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WildernessTest {

    /** A wilderness north of y 100, for the walks below. */
    private val north: (Position) -> Boolean = { it.y >= 100 }

    @Test
    fun `the wilderness is Luna's surface area`() {
        assertTrue(inWilderness(Position(3288, 3888)))
    }

    @Test
    fun `the wilderness dungeons count as the wilderness too`() {
        assertTrue(inWilderness(Position(3000, 10000)))
    }

    @Test
    fun `a town is not the wilderness`() {
        assertFalse(inWilderness(Position(3212, 3423)))
    }

    @Test
    fun `a walk stepping over the border from outside enters the wilderness`() {
        assertTrue(entersWilderness(Position(0, 98), routeTiles(Position(0, 98), listOf(Position(0, 102))), north))
    }

    @Test
    fun `a walk out of the wilderness does not enter it`() {
        assertFalse(entersWilderness(Position(0, 102), routeTiles(Position(0, 102), listOf(Position(0, 98))), north))
    }

    @Test
    fun `a walk that stays outside does not enter the wilderness`() {
        assertFalse(entersWilderness(Position(0, 90), routeTiles(Position(0, 90), listOf(Position(10, 90))), north))
    }

    @Test
    fun `a walk out and back in enters the wilderness`() {
        val route = routeTiles(Position(0, 101), listOf(Position(0, 98), Position(0, 101)))
        assertTrue(entersWilderness(Position(0, 101), route, north))
    }

    @Test
    fun `a route steps diagonally towards each waypoint first then straight`() {
        val tiles = routeTiles(Position(0, 0), listOf(Position(3, 1), Position(3, 0))).toList()
        assertEquals(listOf(Position(1, 1), Position(2, 1), Position(3, 1), Position(3, 0)), tiles)
    }

    @Test
    fun `a route to where it starts steps on nothing`() {
        assertEquals(emptyList<Position>(), routeTiles(Position(5, 5), listOf(Position(5, 5))).toList())
    }
}
