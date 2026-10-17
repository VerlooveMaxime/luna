package game.idle.movement

import api.bot.webwalk.plan.DoorCrossings
import api.bot.webwalk.plan.WalkEstimator
import api.bot.webwalk.plan.ticksForTiles
import io.luna.game.model.Position
import io.luna.game.model.path.GamePathfinder
import io.luna.game.model.path.PathResultType

/**
 * How long the autopilot takes to walk between two tiles by [pathfinder], for the planner to link a trip's ends to its
 * graph: as upstream's `PathfinderWalkEstimator`, no walk through a door of the web (its own edge crosses it, opening it
 * if shut), and none that sets foot in the wilderness from outside it.
 */
class TravelEstimator(
    private val pathfinder: GamePathfinder<Position>,
    private val doors: DoorCrossings,
    private val inWilderness: (Position) -> Boolean,
) : WalkEstimator {

    override fun ticks(from: Position, to: Position): Int? {
        val result = pathfinder.find(from, to)
        if (result.type != PathResultType.COMPLETE && result.type != PathResultType.EMPTY) return null
        if (doors.crosses(from, result.path)) return null
        val tiles = routeTiles(from, result.path)
        if (entersWilderness(from, tiles, inWilderness)) return null
        return ticksForTiles(tiles.count())
    }
}
