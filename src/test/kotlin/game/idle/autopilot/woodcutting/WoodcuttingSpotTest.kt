package game.idle.autopilot.woodcutting

import game.idle.location.Area
import game.idle.location.Tile
import game.idle.location.area
import game.idle.location.parseOne
import game.skill.woodcutting.cutTree.Tree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WoodcuttingSpotTest {

    @Test
    fun `tree names resolve to tree kinds ignoring case, each with its area`() {
        val spots = WoodcuttingSpot.from(parseOne("trees" to mapOf("Normal" to area(1, 2, 3), "OAK" to area(4, 5, 6))))

        assertEquals(setOf(Tree.NORMAL, Tree.OAK), spots.keys)
        assertEquals(Area(Tile(4, 5), 6), spots.getValue(Tree.OAK).area)
    }

    @Test
    fun `tree kinds resolve to the object ids Luna cuts`() {
        val spot = WoodcuttingSpot.from(parseOne()).getValue(Tree.NORMAL)

        assertTrue(TREE_OBJECT_ID in spot.treeObjectIds)
    }

    @Test
    fun `an unknown tree is rejected`() {
        val location = parseOne("id" to "spot", "trees" to mapOf("palm" to area(1, 2, 3)))

        val error = assertThrows<IllegalArgumentException> { WoodcuttingSpot.from(location) }

        assertEquals("Location 'spot' names unknown tree 'palm'", error.message)
    }

    @Test
    fun `a location without trees is rejected`() {
        val location = parseOne("id" to "spot").copy(trees = emptyMap())

        val error = assertThrows<IllegalArgumentException> { WoodcuttingSpot.from(location) }

        assertEquals("Location 'spot' has no trees", error.message)
    }
}
