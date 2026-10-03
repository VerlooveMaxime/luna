package game.idle.autopilot.woodcutting

import game.idle.location.Area
import game.skill.woodcutting.cutTree.Tree
import game.skill.woodcutting.cutTree.TreeStump

/** One kind of tree to cut within an [area], and the object ids Luna cuts for it. */
data class WoodcuttingSpot(val tree: Tree, val area: Area) {

    val treeObjectIds: Set<Int> = TreeStump.ALIVE_TREE_MAP.get(tree).toSet()
}
