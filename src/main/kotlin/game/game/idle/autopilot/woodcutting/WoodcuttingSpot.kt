package game.idle.autopilot.woodcutting

import game.idle.location.Area
import game.idle.location.Location
import game.skill.woodcutting.cutTree.Tree
import game.skill.woodcutting.cutTree.TreeStump

/** One kind of tree at a [Location]: the [area] it grows in and the object ids Luna cuts for it. */
data class WoodcuttingSpot(val location: Location, val tree: Tree, val area: Area) {

    val treeObjectIds: Set<Int> = TreeStump.ALIVE_TREE_MAP.get(tree).toSet()

    companion object {

        /** Every tree kind a location names, resolved; the data file fails at boot when a name is not a tree. */
        fun from(location: Location): Map<Tree, WoodcuttingSpot> {
            require(location.trees.isNotEmpty()) { "Location '${location.id}' has no trees" }
            return location.trees.entries.associate { (name, area) ->
                val tree = requireNotNull(Tree.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }) {
                    "Location '${location.id}' names unknown tree '$name'"
                }
                tree to WoodcuttingSpot(location, tree, area)
            }
        }
    }
}
