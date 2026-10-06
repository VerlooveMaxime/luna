package game.idle.autopilot.woodcutting

import game.idle.movement.Footprint
import game.idle.movement.ReachScan
import game.idle.movement.ReachTerrain
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.Position

/** A tree standing in the world, as the scan needs it. [size] is the longest side of its footprint. */
data class StandingTree(val objectId: Int, val position: Position, val size: Int, val tree: Tree)

/**
 * Turns the trees standing around the chop step's anchor into [TreeCandidate]s ranked by walking distance from the
 * player. A player farther than [maxWalk] tiles from the anchor gets no candidates and walks back first.
 */
class TreeScan(anchor: Position, radius: Int, maxWalk: Int = ReachScan.MAX_WALK) {

    private val scan = ReachScan(anchor, radius, maxWalk)

    fun atLocation(here: Position): Boolean = scan.atLocation(here)

    fun candidates(here: Position, trees: List<StandingTree>, terrain: ReachTerrain<StandingTree>): List<TreeCandidate> =
        scan.reachable(here, trees, { Footprint(it.position, it.size) }, terrain).map { (tree, reach) ->
            TreeCandidate(tree.objectId, tree.position, tree.tree, reach.distance, reach.usableFromHere, reach.approach)
        }
}
