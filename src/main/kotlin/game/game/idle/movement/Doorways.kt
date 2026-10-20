package game.idle.movement

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.WebWalkEdge
import api.bot.webwalk.model.WebWalkGraph
import api.bot.webwalk.plan.PlannerCosts
import io.luna.game.model.Position

/** A door, gate, curtain or entrance of the travel web: the object [id] on [position], [type] the edge that crosses it. */
data class Doorway(val id: Int, val position: Position, val type: EdgeType)

/**
 * The doorways of the travel web by the two side-by-side tiles each stands between, either way across: where a reach
 * scan may step through a shut door (web-walker movement Part 4). With them the web's [entrances] that land on the floor
 * they are used from, by that tile (entrance legs Part 2).
 */
class Doorways(crossings: Map<Pair<Position, Position>, Doorway>, entrances: List<EntranceStep> = emptyList()) {

    private val byStep: Map<Pair<Position, Position>, Doorway> =
        crossings + crossings.mapKeys { (step, _) -> step.second to step.first }

    /** Every tile beside a doorway, so that most steps are answered without a lookup of the pair. */
    private val sides: Set<Position> = byStep.keys.map { it.first }.toSet()

    /** The two tiles each doorway stands between, one way across. */
    private val crossings: Map<Doorway, Pair<Position, Position>> = crossings.entries.associate { (step, doorway) -> doorway to step }

    private val entrances: Map<Position, List<EntranceStep>> = entrances.groupBy { it.from }

    /** The entrances used from [tile]. */
    fun entrancesFrom(tile: Position): List<EntranceStep> = entrances[tile].orEmpty()

    /** The doorway between [a] and [b], two tiles side by side, in either order. */
    fun between(a: Position, b: Position): Doorway? = if (a in sides) byStep[a to b] else null

    /**
     * The doorway a step from [from] to [to] goes through. A diagonal step goes through one when either straight step it
     * could be made of does, as upstream's `DoorCrossings` takes it: through a double door, say.
     */
    fun crossedBy(from: Position, to: Position): Doorway? {
        if (from !in sides && to !in sides) return null
        if (from.x == to.x || from.y == to.y) return between(from, to)
        val sideways = Position(to.x, from.y, from.z)
        val ahead = Position(from.x, to.y, from.z)
        return between(from, sideways) ?: between(sideways, to) ?: between(from, ahead) ?: between(ahead, to)
    }

    /**
     * [door] and the leaf Luna shuts with it (web-walker movement Part 5): a double door's or a gate's other half, the
     * doorway of another id beside it, crossed the same way one tile along the wall.
     */
    fun leavesOf(door: Doorway): Set<Doorway> {
        val (from, to) = crossings[door] ?: return setOf(door)
        val along = if (from.x == to.x) listOf(1 to 0, -1 to 0) else listOf(0 to 1, 0 to -1)
        val beside = along.mapNotNull { (dx, dy) -> between(from.translate(dx, dy), to.translate(dx, dy)) }
        return setOf(door) + beside.filter { it.type == door.type && it.id != door.id }
    }

    companion object {

        val NONE = Doorways(emptyMap())

        /**
         * The doorways of [graph]'s door, gate and curtain edges whose object it names, and its entrance edges that land
         * on the floor they start from, each taking the ticks it says, else the planner's.
         */
        fun of(graph: WebWalkGraph): Doorways {
            val doors = graph.edges.filter { it.type.isDoorLike }.mapNotNull { crossing(graph, it) }.toMap()
            val entrances = graph.edges.filter { it.type == EdgeType.ENTRANCE }.mapNotNull { edge ->
                crossing(graph, edge)?.let { (step, doorway) -> EntranceStep(doorway, step.first, step.second, edge.cost?.toDouble() ?: PlannerCosts().ladderTicks) }
            }
            return Doorways(doors, entrances.filter { it.from.z == it.to.z })
        }

        /** The two tiles [edge] steps between with its doorway, null when the graph does not say them all. */
        private fun crossing(graph: WebWalkGraph, edge: WebWalkEdge): Pair<Pair<Position, Position>, Doorway>? {
            val from = graph.node(edge.from) ?: return null
            val to = graph.node(edge.to) ?: return null
            val doorway = doorwayOf(edge) ?: return null
            return (from.position to to.position) to doorway
        }
    }
}

/** The object [edge] goes through, null when it names no object or no tile for it. */
fun doorwayOf(edge: WebWalkEdge): Doorway? {
    val action = edge.action ?: return null
    val id = action.objectId ?: return null
    val at = action.objectPosition ?: return null
    return Doorway(id, at, edge.type)
}
