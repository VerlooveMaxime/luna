package game.idle.autopilot.woodcutting

import game.idle.location.Location
import game.skill.woodcutting.cutTree.Tree
import game.skill.woodcutting.cutTree.TreeStump

/** A [Location]'s trees resolved to the kinds and object ids Luna cuts. */
data class WoodcuttingSpot(val location: Location, val trees: Set<Tree>) {

    val treeObjectIds: Set<Int> = trees.flatMapTo(mutableSetOf()) { TreeStump.ALIVE_TREE_MAP.get(it) }

    fun has(tree: Tree): Boolean = tree in trees

    companion object {

        fun from(location: Location): WoodcuttingSpot {
            require(location.trees.isNotEmpty()) { "Location '${location.id}' has no trees" }
            val trees = location.trees.mapTo(mutableSetOf()) { name ->
                requireNotNull(Tree.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }) {
                    "Location '${location.id}' names unknown tree '$name'"
                }
            }
            return WoodcuttingSpot(location, trees)
        }
    }
}
