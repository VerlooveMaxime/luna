package game.harness

import io.luna.game.model.Position
import io.luna.game.model.`object`.ObjectType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TileProblemsTest {

    @Test
    fun `open ground with nothing on it has no problem`() {
        assertEquals(emptyList<String>(), TileProblems.standing(standable = true, objects = emptyList()))
    }

    @Test
    fun `ground no neighbour steps onto cannot be stood on`() {
        assertEquals(listOf("a player cannot stand on it"), TileProblems.standing(standable = false, objects = emptyList()))
    }

    @Test
    fun `a wall on the tile is named with how it is placed`() {
        val rock = TileObject("Rock", ObjectType.DIAGONAL_CORNER_WALL)

        assertEquals(listOf("a wall stands on it: Rock (diagonal corner wall)"), TileProblems.standing(standable = true, objects = listOf(rock)))
    }

    @Test
    fun `a diagonal wall reads as a wall, though Luna files it with the interactable objects`() {
        val fence = TileObject("Fence", ObjectType.DIAGONAL_WALL)

        assertEquals(listOf("a wall stands on it: Fence (diagonal wall)"), TileProblems.standing(standable = true, objects = listOf(fence)))
    }

    @Test
    fun `a wall decoration on the tile is named`() {
        val torch = TileObject("Torch", ObjectType.STRAIGHT_INSIDE_WALL_DECORATION)

        assertEquals(listOf("a wall decoration hangs on it: Torch"), TileProblems.standing(standable = true, objects = listOf(torch)))
    }

    @Test
    fun `any other object on the tile is named`() {
        val furnace = TileObject("Furnace", ObjectType.DEFAULT)

        assertEquals(listOf("an object stands on it: Furnace"), TileProblems.standing(standable = true, objects = listOf(furnace)))
    }

    @Test
    fun `an object without a name is told by its kind alone`() {
        val rock = TileObject(null, ObjectType.STRAIGHT_WALL)

        assertEquals(listOf("a wall stands on it (straight wall)"), TileProblems.standing(standable = true, objects = listOf(rock)))
    }

    @Test
    fun `a ground decoration under the player fails nothing`() {
        val flowers = TileObject("Flowers", ObjectType.GROUND_DECORATION)

        assertEquals(emptyList<String>(), TileProblems.standing(standable = true, objects = listOf(flowers)))
    }

    @Test
    fun `a tile no trip the flow allows gets to says where the trip would start`() {
        assertEquals("no trip from 2950, 9512 gets there with this flow's Travel settings", TileProblems.unreachable(Position(2950, 9512, 1)))
    }
}
