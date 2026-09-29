package game.idle.autopilot

import game.idle.AutopilotJob
import game.idle.autopilot.woodcutting.ChopAction
import game.idle.autopilot.woodcutting.WoodcuttingSpot
import game.idle.location.LocationCatalog
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.Position

/** A resolved job: where to chop and which trees. */
data class ChopJob(val spot: WoodcuttingSpot, val action: ChopAction) {

    val summary: String
        get() {
            val kinds = action.trees.sortedBy { it.level }.joinToString(", ") { it.name.lowercase() }
            return "Autopilot: chopping $kinds trees at ${spot.location.name}."
        }
}

/** The answer to an `::idle` request: the job to start, or null with the reason in [message]. */
data class JobRequest(val job: AutopilotJob?, val message: String) {
    companion object {
        fun accepted(job: AutopilotJob, resolved: ChopJob): JobRequest = JobRequest(job, resolved.summary)
        fun rejected(reason: String): JobRequest = JobRequest(null, reason)
    }
}

/** Turns `::idle` arguments and saved jobs into [ChopJob]s. Resolving every location up front validates the data. */
class Jobs(catalog: LocationCatalog) {

    private val spots: Map<String, WoodcuttingSpot> =
        catalog.locations.associate { it.id to WoodcuttingSpot.from(it) }

    val size: Int get() = spots.size

    /** Arguments are an optional location id then tree names: no location means the nearest one, no tree means every kind that grows there. */
    fun fromCommand(args: List<String>, from: Position): JobRequest {
        val locationId = args.firstOrNull()
        val spot = if (locationId == null) nearest(from) else spots[locationId.lowercase()]
        if (spot == null) {
            val known = spots.keys.sorted().joinToString(", ")
            return JobRequest.rejected(
                if (locationId == null) "Autopilot: no locations are defined."
                else "Autopilot: unknown location '$locationId'. Locations: $known",
            )
        }
        val trees = args.drop(1).map { name ->
            val tree = treeNamed(name) ?: return JobRequest.rejected("Autopilot: '$name' is not a kind of tree.")
            if (!spot.has(tree)) {
                val growing = spot.trees.sortedBy { it.level }.joinToString(", ") { it.name.lowercase() }
                return JobRequest.rejected("Autopilot: no $name trees at ${spot.location.name}, only $growing.")
            }
            tree
        }
        val action = ChopAction(if (trees.isEmpty()) spot.trees else trees.toSet())
        val job = AutopilotJob(spot.location.id, action.trees.map { it.name.lowercase() })
        return JobRequest.accepted(job, ChopJob(spot, action))
    }

    /** Null when the saved job no longer matches the data file. */
    fun resolve(job: AutopilotJob): ChopJob? {
        val spot = spots[job.locationId] ?: return null
        val trees = job.trees.map { name -> treeNamed(name)?.takeIf(spot::has) ?: return null }
        if (trees.isEmpty()) {
            return null
        }
        return ChopJob(spot, ChopAction(trees.toSet()))
    }

    private fun nearest(from: Position): WoodcuttingSpot? =
        spots.values.minWithOrNull(
            compareBy<WoodcuttingSpot> { it.location.anchor.toPosition().computeLongestDistance(from) }
                .thenBy { it.location.id },
        )

    private fun treeNamed(name: String): Tree? = Tree.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

/** What `::idle` does: `::idle` alone stops a running autopilot or starts at the nearest location. */
class IdleCommand<P : AutopilotPlayer>(private val autopilot: Autopilot<P>, private val jobs: Jobs) {

    fun run(player: P, args: List<String>, from: Position) {
        if (args.isEmpty() && autopilot.isRunning(player)) {
            autopilot.stop(player)
            player.tell("Autopilot disabled.")
            return
        }
        val request = jobs.fromCommand(args, from)
        request.job?.let { autopilot.start(player, it) }
        player.tell(request.message)
    }
}
