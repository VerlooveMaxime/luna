package game.idle.movement

import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FootprintTest {

    private val range = Footprint(Position(3200, 3200, 0), width = 2, height = 3)

    @Test
    fun `a footprint covers its south-west tile`() {
        assertTrue(Position(3200, 3200, 0) in range)
    }

    @Test
    fun `a footprint covers its north-east tile`() {
        assertTrue(Position(3201, 3202, 0) in range)
    }

    @Test
    fun `a tile west of it is not covered`() {
        assertFalse(Position(3199, 3200, 0) in range)
    }

    @Test
    fun `a tile just past its width is not covered`() {
        assertFalse(Position(3202, 3200, 0) in range)
    }

    @Test
    fun `a tile south of it is not covered`() {
        assertFalse(Position(3200, 3199, 0) in range)
    }

    @Test
    fun `a tile just past its height is not covered`() {
        assertFalse(Position(3200, 3203, 0) in range)
    }

    @Test
    fun `the same tile on another floor is not covered`() {
        assertFalse(Position(3200, 3200, 1) in range)
    }
}
