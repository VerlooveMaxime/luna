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
 * Steps a player needs to walk from one tile to each tile reachable inside some bounds, eight directions, and through
 * entrances. Straight-line distance ignores walls and fences; this does not. A step may be worth more than one
 * ([Step.cost]: a shut door, an entrance), so the search is Dijkstra's over buckets of equal cost, which with plain steps
 * only is a breadth-first search.
 */
class WalkingDistances private constructor(private val steps: Map<Position, Int>, private val crossings: Map<Position, List<Crossing>>) {

    operator fun get(tile: Position): Int? = steps[tile]

    /** The doors and entrances on the cheapest walk to [tile] the search found first, in the order it goes through them. */
    fun crossingsOn(tile: Position): List<Crossing> = crossings[tile] ?: emptyList()

    val reachable: Set<Position> get() = steps.keys

    companion object {

        /** Over plain steps, those [canStep] (from a tile, in a direction) allows. */
        fun from(origin: Position, bounds: TileBounds, canStep: (Position, Direction) -> Boolean): WalkingDistances =
            through(origin, bounds, plain(canStep))

        /**
         * Over the steps of [walk], doors and entrances included. An entrance used inside [bounds] may land anywhere in
         * [landings]; from a landing past [bounds] the walk takes no entrance and steps only onto tiles inside them.
         */
        fun through(origin: Position, bounds: TileBounds, walk: Walk, landings: TileBounds = bounds): WalkingDistances {
            require(origin in bounds) { "Origin $origin lies outside $bounds" }
            val search = Search(origin, bounds, Int.MAX_VALUE, walk, landings)
            search.run { false }
            return WalkingDistances(search.steps, search.crossings)
        }

        /**
         * The first of [goals] a walk from [origin] gets to in at most [maxSteps] steps, or null when none is that
         * close. The search ends at that goal, so a near one costs far less than [from] over the whole range.
         */
        fun firstReached(origin: Position, goals: Set<Position>, maxSteps: Int, canStep: (Position, Direction) -> Boolean): Position? =
            Search(origin, TileBounds.around(origin, maxSteps), maxSteps, plain(canStep)).run { it in goals }

        private fun plain(canStep: (Position, Direction) -> Boolean): Walk =
            Walk { from, direction -> if (canStep(from, direction)) Step.Plain else null }
    }

    /** One search from [origin], recording each tile's steps and the doors and entrances on its way. */
    private class Search(
        origin: Position,
        private val bounds: TileBounds,
        private val maxSteps: Int,
        private val walk: Walk,
        private val landings: TileBounds = bounds,
    ) {

        val steps = hashMapOf(origin to 0)
        /** The crossings on the way to each tile that has any; a tile shares its list with the one it was reached from. */
        val crossings = hashMapOf<Position, List<Crossing>>()

        /** The tiles waiting to be walked on from, by their steps; one reached cheaper since it was queued is passed over. */
        private val queued = mutableListOf(ArrayDeque(listOf(origin)))

        /** Walks out cheapest first; the first tile [wanted] answers yes to, or null. */
        fun run(wanted: (Position) -> Boolean): Position? {
            var cost = 0
            while (cost < queued.size) {
                val tiles = queued[cost]
                while (tiles.isNotEmpty()) {
                    val tile = tiles.removeFirst()
                    if (steps.getValue(tile) < cost) continue
                    if (wanted(tile)) return tile
                    for (direction in Direction.ALL_EXCEPT_NONE) walkOn(tile, cost, direction)
                    if (tile in bounds) for (entrance in walk.entrances(tile)) if (entrance.to in landings) arrive(tile, entrance.to, entrance, cost)
                }
                cost++
            }
            return null
        }

        private fun walkOn(tile: Position, cost: Int, direction: Direction) {
            val neighbour = tile.translate(1, direction)
            val known = steps[neighbour]
            // No step is worth less than one, so a tile known at most one step further gets no shorter way here.
            if (neighbour !in bounds || known != null && known <= cost + 1) return
            val taken = walk.step(tile, direction) ?: return
            arrive(tile, neighbour, taken, cost)
        }

        /** Onto [to] from [tile], reached for [cost], by [taken], unless [to] is known no dearer. */
        private fun arrive(tile: Position, to: Position, taken: Step, cost: Int) {
            val known = steps[to]
            val next = cost + taken.cost
            if (next > maxSteps || known != null && known <= next) return
            steps[to] = next
            val before = crossings[tile]
            val route = if (taken is Crossing) before.orEmpty() + taken else before
            if (route == null) crossings.remove(to) else crossings[to] = route
            while (queued.size <= next) queued += ArrayDeque<Position>()
            queued[next].addLast(to)
        }
    }
}
