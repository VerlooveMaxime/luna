package game.idle.movement

import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ApproachTilesTest {

    private val booth = Position(3186, 3436, 0)

    @Test
    fun `a one tile object is bordered by its eight neighbours`() {
        val tiles = approachTiles(booth, size = 1, from = booth)

        assertEquals(8, tiles.size)
    }

    @Test
    fun `a two tile object is bordered by the twelve tiles around its footprint`() {
        val tiles = approachTiles(Position(3200, 3200, 0), size = 2, from = Position(3200, 3190, 0))

        assertEquals(12, tiles.size)
    }

    @Test
    fun `the tile straight towards the player comes before the diagonals`() {
        val tiles = approachTiles(booth, size = 1, from = Position(3183, 3436, 0))

        assertEquals(Position(3185, 3436, 0), tiles.first())
    }

    @Test
    fun `equally near tiles are ordered west to east then south to north`() {
        val tiles = approachTiles(booth, size = 1, from = booth)

        assertEquals(listOf(Position(3185, 3436, 0), Position(3186, 3435, 0)), tiles.take(2))
    }

    @Test
    fun `candidates stay on the object's plane`() {
        val tiles = approachTiles(Position(3200, 3200, 1), size = 1, from = Position(3200, 3190, 0))

        assertEquals(setOf(1), tiles.map { it.z }.toSet())
    }

    @Test
    fun `size must be positive`() {
        assertThrows<IllegalArgumentException> { approachTiles(booth, size = 0, from = booth) }
    }
}
