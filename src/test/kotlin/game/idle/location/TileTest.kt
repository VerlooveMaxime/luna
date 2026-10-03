package game.idle.location

import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TileTest {

    @Test
    fun `a ground floor tile is written without its floor`() {
        assertEquals("3086 3233", Tile(3086, 3233).text())
    }

    @Test
    fun `an upper floor tile is written with its floor`() {
        assertEquals("3086 3233 1", Tile(3086, 3233, 1).text())
    }

    @Test
    fun `a tile and a position convert both ways`() {
        assertEquals(Position(1, 2, 3), Tile(1, 2, 3).toPosition())
        assertEquals(Tile(1, 2, 3), Tile.of(Position(1, 2, 3)))
    }
}
