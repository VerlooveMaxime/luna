package game.idle.autopilot.bank

import game.idle.location.Bank
import game.idle.location.Tile
import game.testworld.TestWorld
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaBankTerrainTest {

    private val boothId = 2213
    private val wallId = 1902
    private val bank = Bank("test", "Test bank", Tile(3200, 3200))

    private val terrain get() = LunaBankTerrain(TestWorld.world)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `a step onto open ground is allowed`() {
        assertTrue(terrain.canStep(Position(3210, 3210), Direction.EAST))
    }

    @Test
    fun `a step through a wall is refused`() {
        val tile = Position(3210, 3210)
        TestWorld.place(wallId, tile, ObjectType.STRAIGHT_WALL, ObjectDirection.EAST)

        assertFalse(terrain.canStep(tile, Direction.EAST))
    }

    @Test
    fun `a booth is used from the tiles beside it the reach check accepts`() {
        TestWorld.place(boothId, bank.booth.toPosition())

        assertEquals(
            setOf(Position(3199, 3200), Position(3201, 3200), Position(3200, 3199), Position(3200, 3201)),
            terrain.usableFrom(bank).toSet(),
        )
    }

    @Test
    fun `a bank with no booth on its tile is used from nowhere`() {
        assertEquals(emptyList<Position>(), terrain.usableFrom(bank))
    }
}
