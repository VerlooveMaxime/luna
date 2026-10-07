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

    @Test
    fun `an area holds the tiles up to its radius away on each side`() {
        val area = Area(Tile(3200, 3200), radius = 2)

        assertEquals(listOf(true, true, true), listOf(Tile(3200, 3200) in area, Tile(3198, 3202) in area, Tile(3202, 3198) in area))
    }

    @Test
    fun `an area leaves out the tiles beyond its radius`() {
        val area = Area(Tile(3200, 3200), radius = 2)

        assertEquals(listOf(false, false), listOf(Tile(3203, 3200) in area, Tile(3200, 3197) in area))
    }

    @Test
    fun `an area leaves out other floors`() {
        assertEquals(false, Tile(3200, 3200, 1) in Area(Tile(3200, 3200), radius = 2))
    }
}
