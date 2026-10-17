package game.idle.movement

import api.bot.webwalk.data.WebWalkLoader
import api.bot.webwalk.model.WebWalkGraph
import api.bot.webwalk.plan.CapabilitySnapshot
import api.bot.webwalk.plan.DoorCrossings
import api.bot.webwalk.plan.WalkEstimator
import api.bot.webwalk.plan.WebWalkPlan
import api.bot.webwalk.plan.WebWalkPlanner
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.path.route.RoutePathfinder
import io.luna.game.model.path.route.RouteStrategy
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random
import java.util.concurrent.ConcurrentHashMap

/**
 * Upstream's web-walker planner over [travelGraph] of [web], for the autopilot's trips. [estimator] links a trip's ends
 * to that graph and is given its doors, which such a walk never crosses on foot: the door's own edge does.
 */
class TravelWeb(
    web: WebWalkGraph,
    inWilderness: (Position) -> Boolean,
    estimator: (DoorCrossings) -> WalkEstimator,
    private val random: Random = Random(),
) {

    val graph: WebWalkGraph = travelGraph(web, inWilderness)

    val doors: DoorCrossings = DoorCrossings.of(graph)

    private val planner = WebWalkPlanner(graph, estimator(doors))

    /** The tile in front of the booth on [booth] of a bank the web knows, whose walks from the web are linked already. */
    fun bankStand(booth: Position): Position? = graph.banks.firstOrNull { it.booth == booth }?.stand

    /** The cheapest trip from [start] to the nearest of [destinations] that [snapshot] can take, null for none. */
    fun plan(start: Position, destinations: Collection<Position>, snapshot: CapabilitySnapshot): WebWalkPlan? =
        planner.planToNearest(start, destinations, snapshot, intelligence = 1.0, random = random)
}

/** The [TravelWeb] of each world, read from the web-walker's data in [directory] the first time a trip asks for it. */
class TravelWebs(private val directory: Path) {

    private val webs = ConcurrentHashMap<World, TravelWeb>()

    /**
     * The web of [world], null while [directory] has no walk graph: the loader reads a missing file as an empty one,
     * and an empty web kept now would stay empty after the data is generated.
     */
    fun of(world: World): TravelWeb? {
        if (!Files.exists(directory.resolve(WebWalkLoader.WALK_GRAPH))) return null
        return webs.computeIfAbsent(world, ::load)
    }

    private fun load(world: World): TravelWeb {
        // A player's route finder as Pathfinders.forPlayer builds it: one tile wide, held up by walls and objects only.
        val pathfinder = RoutePathfinder(world.collisionManager, 1, 0, RouteStrategy.NORMAL, 1.0)
        return TravelWeb(WebWalkLoader.load(directory), ::inWilderness, { doors -> TravelEstimator(pathfinder, doors, ::inWilderness) })
    }

    companion object {
        /** The data the server generates when it is missing (`webWalkGenerate.kts`). */
        val SERVER = TravelWebs(Path.of("data", "game", "bots", "webwalk"))
    }
}
