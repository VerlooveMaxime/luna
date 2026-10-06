package game.idle.movement

import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ApproachTilesTest {

    private val booth = Position(3186, 3436, 0)
    private val range = 3039
    private val deadTreeId = 1286
    private val wall = 1902
    private val open = Position(3200, 3200)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `a one tile object is bordered by its eight neighbours`() {
        val tiles = approachTiles(booth, width = 1, height = 1, from = booth)

        assertEquals(8, tiles.size)
    }

    @Test
    fun `a two by two object is bordered by the twelve tiles around its footprint`() {
        val tiles = approachTiles(Position(3200, 3200, 0), width = 2, height = 2, from = Position(3200, 3190, 0))

        assertEquals(12, tiles.size)
    }

    @Test
    fun `a two by one object is bordered by ten tiles, the ones in front of it included`() {
        val tiles = approachTiles(Position(3200, 3200, 0), width = 2, height = 1, from = Position(3200, 3190, 0))

        assertEquals(10, tiles.size)
        assertTrue(tiles.containsAll(listOf(Position(3200, 3201, 0), Position(3201, 3201, 0))))
    }

    @Test
    fun `the tile straight towards the player comes before the diagonals`() {
        val tiles = approachTiles(booth, width = 1, height = 1, from = Position(3183, 3436, 0))

        assertEquals(Position(3185, 3436, 0), tiles.first())
    }

    @Test
    fun `equally near tiles are ordered west to east then south to north`() {
        val tiles = approachTiles(booth, width = 1, height = 1, from = booth)

        assertEquals(listOf(Position(3185, 3436, 0), Position(3186, 3435, 0)), tiles.take(2))
    }

    @Test
    fun `candidates stay on the object's plane`() {
        val tiles = approachTiles(Position(3200, 3200, 1), width = 1, height = 1, from = Position(3200, 3190, 0))

        assertEquals(setOf(1), tiles.map { it.z }.toSet())
    }

    @Test
    fun `a footprint is at least one tile each way`() {
        assertThrows<IllegalArgumentException> { approachTiles(booth, width = 0, height = 1, from = booth) }
        assertThrows<IllegalArgumentException> { approachTiles(booth, width = 1, height = 0, from = booth) }
    }

    @Test
    fun `an object turned to face north or south lies across, its sides swapped`() {
        val south = TestWorld.place(range, Position(3200, 3200), direction = ObjectDirection.SOUTH)
        val north = TestWorld.place(range, Position(3210, 3200), direction = ObjectDirection.NORTH)

        assertEquals(Footprint(Position(3200, 3200), width = 2, height = 1), footprintOf(south))
        assertEquals(Footprint(Position(3210, 3200), width = 2, height = 1), footprintOf(north))
    }

    @Test
    fun `open ground can be stood on`() {
        assertTrue(canStandOn(TestWorld.world.collisionManager, open))
    }

    @Test
    fun `a tile something solid stands on cannot`() {
        TestWorld.place(deadTreeId, open)

        assertFalse(canStandOn(TestWorld.world.collisionManager, open))
    }

    @Test
    fun `a tile with a wall along one side still can`() {
        TestWorld.place(wall, open, ObjectType.STRAIGHT_WALL, ObjectDirection.WEST)

        assertTrue(canStandOn(TestWorld.world.collisionManager, open))
    }

    @Test
    fun `an object facing west or east keeps its definition's sides`() {
        val west = TestWorld.place(range, Position(3200, 3200), direction = ObjectDirection.WEST)
        val east = TestWorld.place(range, Position(3210, 3200), direction = ObjectDirection.EAST)

        assertEquals(Footprint(Position(3200, 3200), width = 1, height = 2), footprintOf(west))
        assertEquals(Footprint(Position(3210, 3200), width = 1, height = 2), footprintOf(east))
    }
}
