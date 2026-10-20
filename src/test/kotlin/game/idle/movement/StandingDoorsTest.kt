package game.idle.movement

import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.`object`.ObjectType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StandingDoorsTest {

    private val shut = 1530
    private val open = 1531
    private val wall = 1902
    private val doorTile = Position(3200, 3200)

    private val doors = StandingDoors(TestWorld.world) { if (it == shut) open else null }

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `a door shut on its tile stands`() {
        TestWorld.place(shut, doorTile, ObjectType.STRAIGHT_WALL)
        assertTrue(doors.stands(shut, doorTile))
    }

    @Test
    fun `a door open on the tile beside it stands`() {
        TestWorld.place(open, doorTile.translate(0, 1), ObjectType.STRAIGHT_WALL)
        assertTrue(doors.stands(shut, doorTile))
    }

    @Test
    fun `a gate leaf open two tiles from where it shuts stands`() {
        TestWorld.place(open, doorTile.translate(2, -1), ObjectType.STRAIGHT_WALL)
        assertTrue(doors.stands(shut, doorTile))
    }

    @Test
    fun `an open leaf three tiles away is another door's`() {
        TestWorld.place(open, doorTile.translate(3, 0), ObjectType.STRAIGHT_WALL)
        assertFalse(doors.stands(shut, doorTile))
    }

    @Test
    fun `a door with nothing on or around its tile is removed`() {
        assertFalse(doors.stands(shut, doorTile))
    }

    @Test
    fun `a wall on the door's tile is no door`() {
        TestWorld.place(wall, doorTile, ObjectType.STRAIGHT_WALL)
        assertFalse(doors.stands(shut, doorTile))
    }

    @Test
    fun `a door with no open leaf stands only shut on its tile`() {
        TestWorld.place(open, doorTile.translate(1, 0), ObjectType.STRAIGHT_WALL)
        assertFalse(doors.stands(wall, doorTile))
    }
}
