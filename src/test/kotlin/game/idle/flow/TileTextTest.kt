package game.idle.flow

import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TileTextTest {

    @Test
    fun `a tile is read on the ground floor`() {
        assertEquals(Tile(3101, 3509), TileText.read("3101 3509", "Tile"))
    }

    @Test
    fun `a tile is read with its floor, spaces around ignored`() {
        assertEquals(Tile(3101, 3509, 2), TileText.read(" 3101  3509 2 ", "Tile"))
    }

    @Test
    fun `text that is no tile is refused`() {
        assertRefused("'3101' is not a tile") { TileText.read("3101", "Tile") }
    }

    @Test
    fun `more than three numbers are no tile`() {
        assertRefused("'1 2 3 4' is not a tile") { TileText.read("1 2 3 4", "Tile") }
    }

    @Test
    fun `words among the numbers are no tile`() {
        assertRefused("'3101 north' is not a tile") { TileText.read("3101 north", "Tile") }
    }

    @Test
    fun `a tile has no negative coordinates`() {
        assertRefused("'3101 -1' is not a tile") { TileText.read("3101 -1", "Tile") }
    }

    @Test
    fun `a negative x is refused too`() {
        assertRefused("'-1 3509' is not a tile") { TileText.read("-1 3509", "Tile") }
    }

    @Test
    fun `the floor is 0 to 3`() {
        assertRefused("'3101 3509 4' is not a tile") { TileText.read("3101 3509 4", "Tile") }
    }

    @Test
    fun `a floor below the ground is refused`() {
        assertRefused("'3101 3509 -1' is not a tile") { TileText.read("3101 3509 -1", "Tile") }
    }

    private fun assertRefused(message: String, action: () -> Unit) {
        assertEquals(message, assertThrows<FlowError> { action() }.message)
    }
}
