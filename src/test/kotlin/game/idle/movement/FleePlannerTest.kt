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

    /** A wall running north to south at [x], with no way round inside the planner's reach. */
    private fun wallAt(x: Int): Set<Position> = (3150..3250).map { Position(x, it) }.toSet()

    @Test
    fun `the player runs away from the attacker`() {
        assertEquals(3210, FleePlanner.pick(here, listOf(Position(3199, 3200)), terrain())?.x)
    }

    @Test
    fun `the player runs to the tile farthest from the attacker as the crow flies`() {
        assertEquals(Position(3210, 3210), FleePlanner.pick(here, listOf(Position(3199, 3199)), terrain()))
    }

    @Test
    fun `tiles as far from the attacker are picked south to north`() {
        assertEquals(Position(3210, 3190), FleePlanner.pick(here, listOf(Position(3199, 3200)), terrain()))
    }

    @Test
    fun `tiles as far from the attacker are picked by the fewest steps`() {
        // An east-west wall to the south, open only at x 3200: the south-east corner is 15 steps away, the north-east 10.
        val southWall = (3201..3215).map { Position(it, 3195) }.toSet()

        assertEquals(Position(3210, 3210), FleePlanner.pick(here, listOf(Position(3199, 3200)), terrain(southWall)))
    }

    @Test
    fun `the run never heads back past the attacker`() {
        assertEquals(3200, FleePlanner.pick(here, listOf(Position(3199, 3200), Position(3201, 3200)), terrain())?.x)
    }

    @Test
    fun `a wall keeps the run on its own side`() {
        val tile = FleePlanner.pick(here, listOf(Position(3199, 3200)), terrain(wallAt(3203)))

        assertEquals(3202, tile?.x)
    }

    @Test
    fun `with no attacker there is nothing to run from`() {
        assertNull(FleePlanner.pick(here, emptyList(), terrain()))
    }

    @Test
    fun `boxed in there is nowhere to run`() {
        val box = Direction.ALL_EXCEPT_NONE.map { here.translate(1, it) }.toSet()

        assertNull(FleePlanner.pick(here, listOf(Position(3202, 3200)), terrain(box)))
    }

    @Test
    fun `an attacker with an open way gets at the player`() {
        assertTrue(FleePlanner.canGetAt(Position(3205, 3200), here, terrain()))
    }

    @Test
    fun `a fence keeps an attacker away`() {
        assertFalse(FleePlanner.canGetAt(Position(3205, 3200), here, terrain(wallAt(3202))))
    }

    @Test
    fun `an attacker on another floor cannot get at the player`() {
        assertFalse(FleePlanner.canGetAt(Position(3201, 3200, 1), here, terrain()))
    }

    @Test
    fun `an attacker out of range cannot get at the player`() {
        assertFalse(FleePlanner.canGetAt(Position(3200 + FleePlanner.THREAT_RANGE + 1, 3200), here, terrain()))
    }
}
