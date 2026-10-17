package game.idle.movement

import api.bot.webwalk.plan.DoorCrossings
import io.luna.game.model.Position
import io.luna.game.model.path.GamePathfinder
import io.luna.game.model.path.PathResult
import io.luna.game.model.path.PathResultType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.ArrayDeque

class TravelEstimatorTest {

    private val start = Position(0, 0)

    /** A wilderness north of y 100. */
    private val north: (Position) -> Boolean = { it.y >= 100 }

    /** Answers every search with [type] and [waypoints], as a route's turns. */
    private class FixedPathfinder(private val type: PathResultType, private val waypoints: List<Position>) :
        GamePathfinder<Position>(null) {
        override fun find(origin: Position, target: Position): PathResult<Position> = PathResult(type, ArrayDeque(waypoints))
    }

    private fun estimate(
        to: Position,
        type: PathResultType = PathResultType.COMPLETE,
        waypoints: List<Position> = listOf(to),
        doors: DoorCrossings = DoorCrossings(emptyList()),
        from: Position = start,
    ): Int? = TravelEstimator(FixedPathfinder(type, waypoints), doors, north).ticks(from, to)

    @Test
    fun `a route is run two tiles a tick`() {
        assertEquals(5, estimate(Position(10, 0)))
    }

    @Test
    fun `every tile of a route with turns counts`() {
        assertEquals(4, estimate(Position(4, 4), waypoints = listOf(Position(4, 0), Position(4, 4))))
    }

    @Test
    fun `a walk to the tile it starts on takes no time`() {
        assertEquals(0, estimate(start, type = PathResultType.EMPTY, waypoints = emptyList()))
    }

    @Test
    fun `a route that falls short is no walk`() {
        assertNull(estimate(Position(10, 0), type = PathResultType.PARTIAL))
    }

    @Test
    fun `no route is no walk`() {
        assertNull(estimate(Position(10, 0), type = PathResultType.FAILED, waypoints = emptyList()))
    }

    @Test
    fun `a route through a door of the web is no walk`() {
        assertNull(estimate(Position(10, 0), doors = DoorCrossings(listOf(Position(4, 0) to Position(5, 0)))))
    }

    @Test
    fun `a route into the wilderness is no walk`() {
        assertNull(estimate(Position(0, 102), from = Position(0, 96)))
    }

    @Test
    fun `a route out of the wilderness is a walk`() {
        assertEquals(3, estimate(Position(0, 96), from = Position(0, 102)))
    }
}
