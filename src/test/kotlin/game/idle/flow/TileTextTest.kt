package game.idle.flow

import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TileTextTest {

    @Test
    fun `a tile is read on the ground floor`() {
        assertEquals(Tile(3101, 3509), TileText.read("3101 3509", "walk"))
    }

    @Test
    fun `a tile is read with its floor, spaces around ignored`() {
        assertEquals(Tile(3101, 3509, 2), TileText.read(" 3101  3509 2 ", "walk"))
    }

    @Test
    fun `text that is no tile is refused, naming what needs one`() {
        assertRefused("the work spot needs a tile: x y, or x y floor, not '3101'") { TileText.read("3101", "the work spot") }
    }

    @Test
    fun `more than three numbers are no tile`() {
        assertRefused("walk needs a tile: x y, or x y floor, not '1 2 3 4'") { TileText.read("1 2 3 4", "walk") }
    }

    @Test
    fun `words among the numbers are no tile`() {
        assertRefused("walk needs a tile: x y, or x y floor, not '3101 north'") { TileText.read("3101 north", "walk") }
    }

    @Test
    fun `a tile has no negative coordinates`() {
        assertRefused("A tile has no negative coordinates: 3101 -1") { TileText.read("3101 -1", "walk") }
    }

    @Test
    fun `a negative x is refused too`() {
        assertRefused("A tile has no negative coordinates: -1 3509") { TileText.read("-1 3509", "walk") }
    }

    @Test
    fun `the floor is 0 to 3`() {
        assertRefused("The floor is 0 to 3, not 4") { TileText.read("3101 3509 4", "walk") }
    }

    @Test
    fun `a floor below the ground is refused`() {
        assertRefused("The floor is 0 to 3, not -1") { TileText.read("3101 3509 -1", "walk") }
    }

    private fun assertRefused(message: String, action: () -> Unit) {
        assertEquals(message, assertThrows<FlowError> { action() }.message)
    }
}
