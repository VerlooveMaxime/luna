package game.idle.movement

import io.luna.game.model.Direction
import io.luna.game.model.Position

/** Where a player runs to from npcs that attack them, and whether those npcs can still get at them. */
object FleePlanner {

    /** How far a flight goes before the next look: a few ticks of running. */
    const val MAX_STEPS = 10

    /** How far from the player an attacker is looked for, and how far its walk to them is followed. */
    const val THREAT_RANGE = 16

    /**
     * The tile the player can walk to within [MAX_STEPS] tiles of [here] that lies farthest from the nearest of
     * [threats], as the crow flies; only tiles away from every threat count, so the run never passes one. Fewer steps
     * first on a tie, then west to east and south to north; null when no tile gets farther from them than [here].
     */
    fun pick(here: Position, threats: List<Position>, canStep: (Position, Direction) -> Boolean): Position? {
        if (threats.isEmpty()) return null
        val distances = WalkingDistances.from(here, TileBounds.around(here, MAX_STEPS), canStep)
        // The player's own tile is always reachable and away from every threat, so there is a best tile.
        val best = distances.reachable
            .filter { tile -> threats.all { away(here, tile, it) } }
            .minWith(
                compareByDescending<Position> { safety(it, threats) }
                    .thenBy { distances[it] }
                    .thenBy { it.x }
                    .thenBy { it.y },
            )
        return best.takeIf { safety(it, threats) > safety(here, threats) }
    }

    /**
     * Whether an attacker standing on [from] can walk up beside [target] (within one tile) by [canStep], following
     * its walk no farther than [THREAT_RANGE] from [target]: a fence or a closed gate in between keeps it out.
     */
    fun canGetAt(from: Position, target: Position, canStep: (Position, Direction) -> Boolean): Boolean {
        if (from.z != target.z || from.computeLongestDistance(target) > THREAT_RANGE) return false
        val bounds = TileBounds.around(target, THREAT_RANGE)
        return WalkingDistances.from(from, bounds, canStep).reachable.any { it.computeLongestDistance(target) <= 1 }
    }

    /** Squared distance to the nearest threat. */
    private fun safety(tile: Position, threats: List<Position>): Int = threats.map { squaredDistance(tile, it) }.min()

    /** Whether going from [here] to [tile] does not head towards [threat]. */
    private fun away(here: Position, tile: Position, threat: Position): Boolean =
        (tile.x - here.x) * (here.x - threat.x) + (tile.y - here.y) * (here.y - threat.y) >= 0

    private fun squaredDistance(a: Position, b: Position): Int = (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)
}
