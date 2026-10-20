package game.idle.movement

import api.bot.webwalk.data.WebWalkLoader
import api.bot.webwalk.generate.WebWalkDoorGenerator
import api.bot.webwalk.model.WebWalkGraph
import api.bot.webwalk.plan.BankAccess
import api.bot.webwalk.plan.CapabilitySnapshot
import api.bot.webwalk.plan.DoorCrossings
import api.bot.webwalk.plan.WalkEstimator
import api.bot.webwalk.plan.WebWalkPlan
import api.bot.webwalk.plan.WebWalkPlanner
import api.bot.webwalk.walk.BankStandPlan
import api.bot.webwalk.walk.BankView
import api.bot.webwalk.walk.TripPlan
import api.bot.webwalk.walk.TripPlanner
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
 * to that graph and is given its doors, which such a walk never crosses on foot: the door's own edge does. Objects
 * that [changesForm] are taken only by a player who sees an option on them. A door that no longer [stands] is walked
 * through as an empty doorway.
 */
class TravelWeb(
    web: WebWalkGraph,
    inWilderness: (Position) -> Boolean,
    estimator: (DoorCrossings) -> WalkEstimator,
    private val random: Random = Random(),
    changesForm: (Int) -> Boolean = { false },
    stands: (Int, Position) -> Boolean = { _, _ -> true },
) {

    /** The doors of the web that no longer stand, wilderness ones included. */
    val removedDoors: Set<Doorway> = removedDoors(web, stands)

    val graph: WebWalkGraph = travelGraph(web, inWilderness, removedDoors, changesForm)

    /** The objects of the web that change form, each the [shownFlag] of a player who sees an option on it. */
    val formObjects: Set<Int> = graph.edges.mapNotNull { it.action?.objectId }.filter(changesForm).toSet()

    val doors: DoorCrossings = DoorCrossings.of(graph)

    /** The same doors with their objects, and the entrances that keep to one floor, for the reach scans' walks. */
    val doorways: Doorways = Doorways.of(graph)

    private val planner = WebWalkPlanner(graph, estimator(doors))

    private val trips = TripPlanner(planner, graph.banks)

    /** The tile in front of the booth on [booth] of a bank the web knows, whose walks from the web are linked already. */
    fun bankStand(booth: Position): Position? = graph.banks.firstOrNull { it.booth == booth }?.stand

    /** The cheapest trip from [start] to the nearest of [destinations] that [snapshot] can take, null for none. */
    fun plan(start: Position, destinations: Collection<Position>, snapshot: CapabilitySnapshot): WebWalkPlan? =
        planner.planToNearest(start, destinations, snapshot, intelligence = 1.0, random = random)

    /**
     * Upstream's trip from [start] to [destination] on [means]: straight with what is carried, or by the nearest bank to
     * take what the rest of it spends when that is quicker, counting the time at the booth (Maxime, 2026-10-20).
     */
    fun trip(start: Position, destination: Position, means: TripMeans): TripPlan =
        trips.plan(start, destination, means.carried, means.tiers, means.view, intelligence = 1.0, random = random)

    /** The trip on from the bank stand at [stand], with [tier] of [means]'s bank, and what it would take there; null for none. */
    fun fromBank(stand: Position, destination: Position, tier: CapabilitySnapshot, means: TripMeans): BankStandPlan? =
        trips.fromBank(stand, destination, tier, means.carried, means.view, random = random)
}

/**
 * What a trip may count on (Maxime, 2026-10-20): what the player [carried] carries and the bank's [tiers] (none
 * without bank stops), each under the flow's Travel rules, and the bag and the bank as a withdrawal reads them ([view]).
 */
class TripMeans(val carried: CapabilitySnapshot, val tiers: Map<BankAccess, CapabilitySnapshot>, val view: BankView) {

    /** Whether [other] counts on the same: planning on it would find the same trips. The bag's room matters only with the bank. */
    fun sameAs(other: TripMeans): Boolean =
        alike(carried, other.carried) && tiers.keys == other.tiers.keys && tiers.all { (access, tier) -> alike(tier, other.tiers.getValue(access)) } &&
            (tiers.isEmpty() || view == other.view)

    companion object {

        /** Whether [one] and [other] let a trip spend the same. */
        fun alike(one: CapabilitySnapshot, other: CapabilitySnapshot): Boolean =
            one.items == other.items && one.usableTeleports == other.usableTeleports && one.charges == other.charges && one.teleportSpends == other.teleportSpends
    }
}

/**
 * The [TravelWeb] of each world, read from the web-walker's data in [directory] the first time a trip asks for it, with
 * Luna's door files in [doorDirectory] naming each door's open leaf.
 */
class TravelWebs(private val directory: Path, private val doorDirectory: Path = Path.of("data", "game", "world", "doors")) {

    private val webs = ConcurrentHashMap<World, TravelWeb>()

    /**
     * The web of [world], null while [directory] has no walk graph: the loader reads a missing file as an empty one,
     * and an empty web kept now would stay empty after the data is generated.
     */
    fun of(world: World): TravelWeb? {
        if (!Files.exists(directory.resolve(WebWalkLoader.WALK_GRAPH))) return null
        return webs.computeIfAbsent(world, ::load)
    }

    /** The doorways of [world]'s web, none while it has no data: every closed door is then a wall to the reach scans. */
    fun doorwaysOf(world: World): Doorways = of(world)?.doorways ?: Doorways.NONE

    private fun load(world: World): TravelWeb {
        // A player's route finder as Pathfinders.forPlayer builds it: one tile wide, held up by walls and objects only.
        val pathfinder = RoutePathfinder(world.collisionManager, 1, 0, RouteStrategy.NORMAL, 1.0)
        // Read here rather than from Luna's Doors, which parse the same files off the game thread at launch.
        val openIds = WebWalkDoorGenerator.readDoors(doorDirectory).associate { it.closed to it.open }
        return TravelWeb(
            WebWalkLoader.load(directory), ::inWilderness, { doors -> TravelEstimator(pathfinder, doors, ::inWilderness) },
            changesForm = ObjectForms.SERVER::changesForm, stands = StandingDoors(world, openIds::get)::stands,
        )
    }

    companion object {
        /** The data the server generates when it is missing (`webWalkGenerate.kts`). */
        val SERVER = TravelWebs(Path.of("data", "game", "bots", "webwalk"))
    }
}
