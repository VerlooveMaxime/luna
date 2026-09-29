package game.idle.autopilot.woodcutting

import game.idle.location.parseOne
import game.skill.woodcutting.cutTree.Tree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class WoodcuttingSpotTest {

    @Test
    fun `tree names resolve to tree kinds ignoring case`() {
        val spot = WoodcuttingSpot.from(parseOne("trees" to listOf("Normal", "OAK")))

        assertEquals(setOf(Tree.NORMAL, Tree.OAK), spot.trees)
    }

    @Test
    fun `tree kinds resolve to the object ids Luna cuts`() {
        val spot = WoodcuttingSpot.from(parseOne("trees" to listOf("normal")))

        assertTrue(TREE_OBJECT_ID in spot.treeObjectIds)
    }

    @Test
    fun `has tells whether a tree kind grows here`() {
        val spot = WoodcuttingSpot.from(parseOne("trees" to listOf("normal")))

        assertTrue(spot.has(Tree.NORMAL))
        assertFalse(spot.has(Tree.OAK))
    }

    @Test
    fun `an unknown tree is rejected`() {
        val location = parseOne("id" to "spot", "trees" to listOf("palm"))

        val error = assertThrows<IllegalArgumentException> { WoodcuttingSpot.from(location) }

        assertEquals("Location 'spot' names unknown tree 'palm'", error.message)
    }

    @Test
    fun `a location without trees is rejected`() {
        val location = parseOne("id" to "spot").copy(trees = emptyList())

        val error = assertThrows<IllegalArgumentException> { WoodcuttingSpot.from(location) }

        assertEquals("Location 'spot' has no trees", error.message)
    }
}
