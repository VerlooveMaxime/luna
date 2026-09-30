package game.idle.location.survey

import game.idle.location.Tile
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.cache.map.MapObject
import io.luna.game.model.Position
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SurveyMapTest {

    private val treeKinds = mapOf(1278 to Tree.NORMAL, 1308 to Tree.WILLOW)
    private val boothIds = setOf(2213)

    private fun cached(id: Int, x: Int, y: Int, z: Int = 0) =
        MapObject(id, Position(x, y, z), ObjectType.DEFAULT, ObjectDirection.NORTH)

    @Test
    fun `from keeps each tree placement with its kind`() {
        val map = SurveyMap.from(listOf(cached(1308, 3087, 3235, z = 1)), treeKinds, boothIds)

        assertEquals(listOf(TreePlacement(Tree.WILLOW, 1308, Tile(3087, 3235, 1))), map.trees)
    }

    @Test
    fun `from keeps each booth placement`() {
        val map = SurveyMap.from(listOf(cached(2213, 3186, 3440)), treeKinds, boothIds)

        assertEquals(listOf(Tile(3186, 3440)), map.booths)
    }

    @Test
    fun `from drops objects that are neither trees nor booths`() {
        val map = SurveyMap.from(listOf(cached(1530, 3190, 3450)), treeKinds, boothIds)

        assertEquals(0, map.trees.size + map.booths.size)
    }

    @Test
    fun `trees on the edge of the square are within the radius`() {
        val map = surveyMap(placed(105, 95), placed(106, 100))

        assertEquals(listOf(placed(105, 95)), map.treesWithin(Tile(100, 100), radius = 5))
    }

    @Test
    fun `trees on another floor are never within the radius`() {
        val map = surveyMap(placed(100, 100, z = 1))

        assertEquals(emptyList<TreePlacement>(), map.treesWithin(Tile(100, 100), radius = 5))
    }

    @Test
    fun `a booth tile holds a booth`() {
        assertTrue(surveyMap(booths = listOf(Tile(3186, 3440))).hasBooth(Tile(3186, 3440)))
    }

    @Test
    fun `a tile next to a booth holds none`() {
        assertFalse(surveyMap(booths = listOf(Tile(3186, 3440))).hasBooth(Tile(3186, 3441)))
    }

    @Test
    fun `the nearest booth on the floor wins`() {
        val map = surveyMap(booths = listOf(Tile(130, 100), Tile(90, 110), Tile(100, 101, z = 1)))

        assertEquals(BoothDistance(Tile(90, 110), distance = 10), map.nearestBooth(Tile(100, 100)))
    }

    @Test
    fun `a floor without booths has no nearest booth`() {
        assertNull(surveyMap(booths = listOf(Tile(100, 100, z = 2))).nearestBooth(Tile(100, 100)))
    }

    @Test
    fun `tiles apart counts the longer axis`() {
        assertEquals(7, tilesApart(Tile(100, 100), Tile(97, 107)))
    }

    @Test
    fun `tiles on different floors are never near`() {
        assertEquals(Int.MAX_VALUE, tilesApart(Tile(100, 100), Tile(100, 100, z = 1)))
    }
}
