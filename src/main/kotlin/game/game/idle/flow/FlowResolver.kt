package game.idle.flow

import game.idle.autopilot.woodcutting.ChopAction
import game.idle.autopilot.woodcutting.WoodcuttingSpot
import game.idle.location.Location
import game.idle.location.LocationCatalog
import game.idle.location.Tile
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.Position

/** A step checked against the data: every name became the thing it names. */
sealed interface ResolvedStep {

    data class Chop(val spot: WoodcuttingSpot, val action: ChopAction) : ResolvedStep

    /** [itemIds] is what the chop steps before it gather. */
    data class Drop(val itemIds: Set<Int>) : ResolvedStep

    data class Bank(val location: Location, val booth: Tile) : ResolvedStep
}

/** Checks flow lines against the locations; resolving every location up front validates the data file at boot. */
class FlowResolver(catalog: LocationCatalog) {

    private val spots: Map<String, Map<Tree, WoodcuttingSpot>> =
        catalog.locations.associate { it.id to WoodcuttingSpot.from(it) }

    val size: Int get() = spots.size

    val locationIds: List<String> get() = spots.keys.sorted()

    /** The kinds of tree at a location, easiest first; empty for an unknown location. */
    fun treesAt(locationId: String): List<String> =
        spots[locationId.lowercase()]?.keys.orEmpty().sortedBy { it.level }.map { it.name.lowercase() }

    /** Throws [FlowError] naming the first line that is wrong. */
    fun resolve(lines: List<String>): List<ResolvedStep> {
        var at: Location? = null
        val gathered = mutableSetOf<Int>()
        return lines.mapIndexed { index, line ->
            val step = try {
                resolve(FlowParser.parse(line), at, gathered)
            } catch (e: FlowError) {
                throw FlowError("Step ${index + 1}: ${e.message}")
            }
            if (step is ResolvedStep.Chop) {
                at = step.spot.location
                gathered += step.spot.tree.logId
            }
            step
        }
    }

    /** The chop line behind `::idle`: the spot nearest to [from]. */
    fun nearestChopLine(from: Position): String? {
        val spot = spots.values.flatMap { it.values }.minWithOrNull(
            compareBy<WoodcuttingSpot> { it.area.anchor.toPosition().computeLongestDistance(from) }
                .thenBy { it.location.id }
                .thenBy { it.tree.level },
        ) ?: return null
        return chopLine(spot)
    }

    /** The chop line behind `::idle` with a location and an optional tree name; without one, the easiest tree there. */
    fun chopLine(locationId: String, treeName: String?): String {
        val here = spotsAt(locationId)
        val spot = if (treeName == null) here.values.minBy { it.tree.level } else spot(treeName, here)
        return chopLine(spot)
    }

    private fun chopLine(spot: WoodcuttingSpot): String = "chop ${spot.tree.name.lowercase()} @${spot.location.id}"

    private fun resolve(step: FlowStep, at: Location?, gathered: Set<Int>): ResolvedStep =
        when (step) {
            is FlowStep.Chop -> {
                val spot = spot(step.tree, spotsAt(step.locationId))
                ResolvedStep.Chop(spot, ChopAction(setOf(spot.tree)))
            }
            FlowStep.Drop -> {
                if (gathered.isEmpty()) throw FlowError("drop comes after a chop step, so the flow knows what to drop")
                ResolvedStep.Drop(gathered.toSet())
            }
            FlowStep.BankDepositAll -> {
                if (at == null) throw FlowError("bank comes after a chop step, so the flow knows which bank to use")
                val booth = at.bank ?: throw FlowError("${at.name} has no bank")
                ResolvedStep.Bank(at, booth)
            }
        }

    private fun spotsAt(locationId: String): Map<Tree, WoodcuttingSpot> =
        spots[locationId.lowercase()]
            ?: throw FlowError("Unknown location '$locationId'. Locations: ${locationIds.joinToString(", ")}")

    private fun spot(name: String, here: Map<Tree, WoodcuttingSpot>): WoodcuttingSpot {
        val tree = Tree.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw FlowError("'$name' is not a kind of tree")
        return here[tree] ?: run {
            val location = here.values.first().location
            val growing = here.keys.sortedBy { it.level }.joinToString(", ") { it.name.lowercase() }
            throw FlowError("No $name trees at ${location.name}, only $growing")
        }
    }
}
