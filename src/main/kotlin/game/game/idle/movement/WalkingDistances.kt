package game.idle.movement

import io.luna.game.model.Direction
import io.luna.game.model.Position

/** An axis-aligned rectangle of tiles on one floor. */
data class TileBounds(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int, val z: Int) {

    init {
        require(minX <= maxX && minY <= maxY) { "Empty bounds: $this" }
    }

    operator fun contains(tile: Position): Boolean =
        tile.z == z && tile.x in minX..maxX && tile.y in minY..maxY

    /** The smallest bounds holding both this and [tile] (same floor). */
    fun including(tile: Position): TileBounds {
        require(tile.z == z) { "Tile $tile is not on floor $z" }
        return TileBounds(minOf(minX, tile.x), minOf(minY, tile.y), maxOf(maxX, tile.x), maxOf(maxY, tile.y), z)
    }

    companion object {
        fun around(centre: Position, radius: Int): TileBounds {
            require(radius >= 0) { "radius must not be negative, got $radius" }
            return TileBounds(centre.x - radius, centre.y - radius, centre.x + radius, centre.y + radius, centre.z)
        }
    }
}

/**
 * Steps a player needs to walk from one tile to each tile reachable inside some bounds, eight directions, computed
 * with a breadth-first search over [canStep] (from a tile, in a direction). Straight-line distance ignores walls
 * and fences; this does not.
 */
class WalkingDistances private constructor(private val steps: Map<Position, Int>) {

    operator fun get(tile: Position): Int? = steps[tile]

    val reachable: Set<Position> get() = steps.keys

    companion object {

        fun from(origin: Position, bounds: TileBounds, canStep: (Position, Direction) -> Boolean): WalkingDistances {
            require(origin in bounds) { "Origin $origin lies outside $bounds" }
            val steps = mutableMapOf(origin to 0)
            walk(origin, bounds, Int.MAX_VALUE, canStep, steps) { false }
            return WalkingDistances(steps)
        }

        /**
         * The first of [goals] a walk from [origin] gets to in at most [maxSteps] steps, or null when none is that
         * close. The search ends at that goal, so a near one costs far less than [from] over the whole range.
         */
        fun firstReached(origin: Position, goals: Set<Position>, maxSteps: Int, canStep: (Position, Direction) -> Boolean): Position? =
            walk(origin, TileBounds.around(origin, maxSteps), maxSteps, canStep, mutableMapOf(origin to 0)) { it in goals }

        /** Breadth-first from [origin], recording each tile's steps; the first tile [wanted] answers yes to, or null. */
        private fun walk(
            origin: Position,
            bounds: TileBounds,
            maxSteps: Int,
            canStep: (Position, Direction) -> Boolean,
            steps: MutableMap<Position, Int>,
            wanted: (Position) -> Boolean,
        ): Position? {
            val queue = ArrayDeque(listOf(origin))
            while (queue.isNotEmpty()) {
                val tile = queue.removeFirst()
                if (wanted(tile)) return tile
                val next = steps.getValue(tile) + 1
                if (next > maxSteps) continue
                for (direction in Direction.ALL_EXCEPT_NONE) {
                    val neighbour = tile.translate(1, direction)
                    if (neighbour in bounds && neighbour !in steps && canStep(tile, direction)) {
                        steps[neighbour] = next
                        queue.addLast(neighbour)
                    }
                }
            }
            return null
        }
    }
}
