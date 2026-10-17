package game.idle.movement

import engine.controllers.WildernessLocatableController
import io.luna.game.model.Position

/** Whether [position] lies in Luna's wilderness, surface or dungeons. */
fun inWilderness(position: Position): Boolean = WildernessLocatableController.inside(position)

/**
 * Whether a walk from [start] over [tiles], in order, sets foot in the wilderness from outside it. No automation
 * enters the wilderness unless the player said so (Maxime, 2026-10-15); a walk that starts in it may walk out.
 */
fun entersWilderness(start: Position, tiles: Sequence<Position>, inWilderness: (Position) -> Boolean): Boolean {
    var outside = !inWilderness(start)
    for (tile in tiles) {
        val inside = inWilderness(tile)
        if (outside && inside) return true
        outside = !inside
    }
    return false
}

/**
 * The tiles a walk from [start] through [waypoints] steps on, in order, without [start]: towards each waypoint
 * diagonally first, then straight, as a route's waypoints mark where it turns.
 */
fun routeTiles(start: Position, waypoints: Iterable<Position>): Sequence<Position> = sequence {
    var current = start
    for (waypoint in waypoints) {
        while (current != waypoint) {
            current = Position(
                current.x + Integer.signum(waypoint.x - current.x),
                current.y + Integer.signum(waypoint.y - current.y),
                current.z,
            )
            yield(current)
        }
    }
}
