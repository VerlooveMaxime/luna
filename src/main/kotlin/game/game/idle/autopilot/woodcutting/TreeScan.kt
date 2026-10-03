package game.idle.autopilot.woodcutting

import game.idle.movement.TileBounds
import game.idle.movement.WalkingDistances
import game.idle.movement.approachTiles
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.Direction
import io.luna.game.model.Position

/** A tree standing in the world, as the scan needs it. [size] is the longest side of its footprint. */
data class StandingTree(val objectId: Int, val position: Position, val size: Int, val tree: Tree)

/** The collision answers a scan needs. [LunaWoodcutter] answers from the collision manager. */
interface Terrain {

    fun canStep(from: Position, direction: Direction): Boolean

    fun isBlocked(tile: Position): Boolean

    /** Whether a player standing on [tile] can chop [tree] without moving. */
    fun reachedFrom(tile: Position, tree: StandingTree): Boolean
}

/**
 * Turns the trees standing around the chop step's anchor into [TreeCandidate]s ranked by walking distance from the
 * player. A player farther than [maxWalk] tiles from the anchor gets no candidates and walks back first.
 */
class TreeScan(private val anchor: Position, private val radius: Int, private val maxWalk: Int = MAX_WALK) {

    private val area: TileBounds = TileBounds.around(anchor, radius)

    fun atLocation(here: Position): Boolean = here in area

    fun candidates(here: Position, trees: List<StandingTree>, terrain: Terrain): List<TreeCandidate> {
        if (here.z != anchor.z || here.computeLongestDistance(anchor) > maxWalk) {
            return emptyList()
        }
        val distances = WalkingDistances.from(here, area.including(here), terrain::canStep)
        return trees.mapNotNull { candidate(here, it, distances, terrain) }
    }

    /** Null when no tile the tree can be cut from is reachable on foot. */
    private fun candidate(
        here: Position,
        tree: StandingTree,
        distances: WalkingDistances,
        terrain: Terrain,
    ): TreeCandidate? {
        if (terrain.reachedFrom(here, tree)) {
            return TreeCandidate(tree.objectId, tree.position, tree.tree, distance = 0, usableFromHere = true, approach = here)
        }
        val (approach, steps) = approachTiles(tree.position, tree.size, from = here)
            .filter { !terrain.isBlocked(it) && terrain.reachedFrom(it, tree) }
            .mapNotNull { tile -> distances[tile]?.let { tile to it } }
            .minByOrNull { (_, steps) -> steps }
            ?: return null
        return TreeCandidate(tree.objectId, tree.position, tree.tree, steps, usableFromHere = false, approach)
    }

    companion object {
        const val MAX_WALK = 48
    }
}
