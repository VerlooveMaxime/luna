package game.idle.movement

import io.luna.game.model.Direction
import io.luna.game.model.Position

/** Something to walk up to: its south-west tile and the longest side of its footprint. */
data class Footprint(val position: Position, val size: Int)

/** How a player gets to use a target: [distance] walking steps to [approach], zero when [usableFromHere]. */
data class Reach(val distance: Int, val usableFromHere: Boolean, val approach: Position)

/** The collision answers a scan for targets of type [T] needs. */
interface ReachTerrain<in T> {

    fun canStep(from: Position, direction: Direction): Boolean

    fun isBlocked(tile: Position): Boolean

    /**
     * Whether a player standing on [tile] can use [target] without moving, by the same rule as the click's listener:
     * Luna checks an npc or an object differently from a bare tile (an npc must be faced, not diagonal).
     */
    fun reachedFrom(tile: Position, target: T): Boolean
}

/**
 * Ranks targets standing around an [anchor] (fishing spots, fires) by the walking distance from the player to a tile
 * they can be used from. A player farther than [maxWalk] tiles from the anchor gets nothing and walks back first.
 */
class ReachScan(private val anchor: Position, radius: Int, private val maxWalk: Int = MAX_WALK) {

    private val area: TileBounds = TileBounds.around(anchor, radius)

    fun atLocation(here: Position): Boolean = here in area

    /** Each target the player can walk up to, with how; unreachable ones are left out. */
    fun <T> reachable(here: Position, targets: List<T>, footprint: (T) -> Footprint, terrain: ReachTerrain<T>): List<Pair<T, Reach>> {
        if (here.z != anchor.z || here.computeLongestDistance(anchor) > maxWalk) {
            return emptyList()
        }
        val distances = WalkingDistances.from(here, area.including(here), terrain::canStep)
        return targets.mapNotNull { target -> reach(here, target, footprint(target), distances, terrain)?.let { target to it } }
    }

    private fun <T> reach(here: Position, target: T, placed: Footprint, distances: WalkingDistances, terrain: ReachTerrain<T>): Reach? {
        if (terrain.reachedFrom(here, target)) {
            return Reach(distance = 0, usableFromHere = true, approach = here)
        }
        val (approach, steps) = approachTiles(placed.position, placed.size, from = here)
            .filter { !terrain.isBlocked(it) && terrain.reachedFrom(it, target) }
            .mapNotNull { tile -> distances[tile]?.let { tile to it } }
            .minByOrNull { (_, steps) -> steps }
            ?: return null
        return Reach(steps, usableFromHere = false, approach)
    }

    companion object {
        const val MAX_WALK = 48
    }
}
