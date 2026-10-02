package game.idle.tutorial

import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DoorTest {

    private val door = TutorialFixtures.door

    @Test
    fun `the tile across a west wall is the one to the west`() {
        assertEquals(Position(3204, 3200), door.across)
    }

    @Test
    fun `the tile across a north wall is the one to the north`() {
        assertEquals(Position(3205, 3201), door.copy(side = WallSide.NORTH).across)
    }

    @Test
    fun `an open door is turned a quarter from the shut one`() {
        assertEquals(WallSide.NORTH.rotation, door.openRotation)
    }

    @Test
    fun `an open door on a south wall turns back to the first rotation`() {
        assertEquals(WallSide.WEST.rotation, door.copy(side = WallSide.SOUTH).openRotation)
    }

    @Test
    fun `going through from the door's tile ends across the wall`() {
        assertEquals(door.across, door.destination(door.tile))
    }

    @Test
    fun `going through from across the wall ends on the door's tile`() {
        assertEquals(door.tile, door.destination(door.across))
    }

    @Test
    fun `opening takes the shut door away and draws the open one across the wall`() {
        val expected = DoorPiece(3014, door.tile, WallSide.WEST.rotation) to DoorPiece(1535, door.across, door.openRotation)

        assertEquals(expected, door.change(open = true))
    }

    @Test
    fun `shutting takes the open door away and draws the shut one again`() {
        val expected = DoorPiece(1535, door.across, door.openRotation) to DoorPiece(3014, door.tile, WallSide.WEST.rotation)

        assertEquals(expected, door.change(open = false))
    }
}
