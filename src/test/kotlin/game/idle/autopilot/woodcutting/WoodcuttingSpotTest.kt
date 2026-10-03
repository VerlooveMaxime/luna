package game.idle.autopilot.woodcutting

import game.idle.location.Area
import game.idle.location.Tile
import game.skill.woodcutting.cutTree.Tree
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WoodcuttingSpotTest {

    @Test
    fun `a tree kind resolves to the object ids Luna cuts`() {
        val spot = WoodcuttingSpot(Tree.NORMAL, Area(Tile(3165, 3445), 15))

        assertTrue(TREE_OBJECT_ID in spot.treeObjectIds)
    }
}
