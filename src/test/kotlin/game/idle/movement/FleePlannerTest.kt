package game.idle.movement

import io.luna.game.model.Direction
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FleePlannerTest {

    private val here = Position(3200, 3200)

    /** Every tile can be walked onto except [walls]. */
    private fun terrain(walls: Set<Position> = emptySet()): (Position, Direction) -> Boolean =
        { from, direction -> from.translate(1, direction) !in walls }

    /** The flight's tile from [threats] over [walls], with no door anywhere. */
    private fun pick(threats: List<Position>, walls: Set<Position> = emptySet()): Position? {
        val canStep = terrain(walls)
        val step = { from: Position, direction: Direction -> if (canStep(from, direction)) Step.Plain else null }
        return FleePlanner.pick(here, threats, step, ShutOut(here, threats, canStep, canStep, Doorways.NONE))?.tile
    }

    /** The flight from [threats] round [room]. */
    private fun pick(room: FleeRoom, threats: List<Position>): FlightRoute? = FleePlanner.pick(here, threats, room::step, room.shutOut(here, threats))

    /** A wall running north to south at [x], with no way round inside the planner's reach. */
    private fun wallAt(x: Int): Set<Position> = (3150..3250).map { Position(x, it) }.toSet()

    @Test
    fun `the player runs away from the attacker`() {
        assertEquals(3210, pick(listOf(Position(3199, 3200)))?.x)
    }

    @Test
    fun `the player runs to the tile farthest from the attacker as the crow flies`() {
        assertEquals(Position(3210, 3210), pick(listOf(Position(3199, 3199))))
    }

    @Test
    fun `tiles as far from the attacker are picked south to north`() {
        assertEquals(Position(3210, 3190), pick(listOf(Position(3199, 3200))))
    }

    @Test
    fun `tiles as far from the attacker are picked by the fewest steps`() {
        // An east-west wall to the south, open only at x 3200: the south-east corner is 15 steps away, the north-east 10.
        val southWall = (3201..3215).map { Position(it, 3195) }.toSet()

        assertEquals(Position(3210, 3210), pick(listOf(Position(3199, 3200)), southWall))
    }

    @Test
    fun `the run never heads back past the attacker`() {
        assertEquals(3200, pick(listOf(Position(3199, 3200), Position(3201, 3200)))?.x)
    }

    @Test
    fun `a wall keeps the run on its own side`() {
        assertEquals(3202, pick(listOf(Position(3199, 3200)), wallAt(3203))?.x)
    }

    @Test
    fun `with no attacker there is nothing to run from`() {
        assertNull(pick(emptyList()))
    }

    @Test
    fun `cornered, the run goes past the attacker to open ground`() {
        // A corridor running west from the player, walled on every other side; the attacker stands in it, next to them.
        val corridor = (3190..3200).map { Position(it, 3200) }.toSet()
        val around = TileBounds.around(here, FleePlanner.MAX_STEPS)
        val walls = (around.minX..around.maxX).flatMap { x -> (around.minY..around.maxY).map { y -> Position(x, y) } }.toSet() - corridor

        assertEquals(Position(3190, 3200), pick(listOf(Position(3199, 3200)), walls))
    }

    @Test
    fun `boxed in there is nowhere to run`() {
        val box = Direction.ALL_EXCEPT_NONE.map { here.translate(1, it) }.toSet()

        assertNull(pick(listOf(Position(3202, 3200)), box))
    }

    @Test
    fun `the run goes to the nearest tile behind a door that keeps the attacker out once shut`() {
        val room = FleeRoom()

        val route = pick(room, listOf(Position(3197, 3200)))

        assertEquals(FlightRoute(room.door.position, listOf(DoorStep(room.door, room.outside, room.door.position, shut = true))), route)
    }

    @Test
    fun `an open door on the way is walked through, still named`() {
        val room = FleeRoom(shut = false)

        val route = pick(room, listOf(Position(3197, 3200)))

        assertEquals(room.door.position to listOf(room.door to false), route?.tile to route?.doors?.map { it.doorway to it.shut })
    }

    @Test
    fun `a room the attacker can get into another way is no shelter`() {
        val route = pick(FleeRoom(backWay = true), listOf(Position(3197, 3200)))

        assertEquals(3210 to emptyList<DoorStep>(), route?.tile?.x to route?.doors)
    }

    @Test
    fun `a door towards the attacker is no way out`() {
        val route = pick(FleeRoom(), listOf(Position(3201, 3199)))

        assertEquals(emptyList<DoorStep>(), route?.doors)
    }

    @Test
    fun `an attacker with an open way gets at the player`() {
        assertTrue(FleePlanner.canGetAt(Position(3205, 3200), here, terrain(), terrain()))
    }

    @Test
    fun `an attacker gets at the player from beside them, its walk never onto their tile`() {
        assertTrue(FleePlanner.canGetAt(Position(3205, 3200), here, terrain(walls = setOf(here)), terrain()))
    }

    @Test
    fun `a fence keeps an attacker away`() {
        assertFalse(FleePlanner.canGetAt(Position(3205, 3200), here, terrain(wallAt(3202)), terrain(wallAt(3202))))
    }

    @Test
    fun `an attacker right behind a wall cannot get at the player`() {
        // A wall along the line between x 3200 and 3201, the whole height of the attacker's walk.
        val wall = { from: Position, direction: Direction -> (from.x <= 3200) == (from.translate(1, direction).x <= 3200) }

        assertFalse(FleePlanner.canGetAt(Position(3201, 3200), here, wall, wall))
    }

    @Test
    fun `an attacker on another floor cannot get at the player`() {
        assertFalse(FleePlanner.canGetAt(Position(3201, 3200, 1), here, terrain(), terrain()))
    }

    @Test
    fun `an attacker out of range cannot get at the player`() {
        assertFalse(FleePlanner.canGetAt(Position(3200 + FleePlanner.THREAT_RANGE + 1, 3200), here, terrain(), terrain()))
    }
}
