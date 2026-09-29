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

    data class Chop(val spot: WoodcuttingSpot, val action: ChopAction, val until: Until?) : ResolvedStep

    data class Bank(val location: Location, val booth: Tile) : ResolvedStep

    data object Loop : ResolvedStep
}

/** Checks flow lines against the locations; resolving every location up front validates the data file at boot. */
class FlowResolver(catalog: LocationCatalog) {

    private val spots: Map<String, WoodcuttingSpot> =
        catalog.locations.associate { it.id to WoodcuttingSpot.from(it) }

    val size: Int get() = spots.size

    val locationIds: List<String> get() = spots.keys.sorted()

    /** Throws [FlowError] naming the first line that is wrong. */
    fun resolve(lines: List<String>): List<ResolvedStep> {
        var at: Location? = null
        return lines.mapIndexed { index, line ->
            val step = try {
                resolve(FlowParser.parse(line), at)
            } catch (e: FlowError) {
                throw FlowError("Step ${index + 1}: ${e.message}")
            }
            if (step is ResolvedStep.Chop) at = step.spot.location
            step
        }
    }

    /** The one-line flow behind `::idle`: every kind growing at the location nearest to [from], power-chopped. */
    fun nearestChopLine(from: Position): String? {
        val spot = spots.values.minWithOrNull(
            compareBy<WoodcuttingSpot> { it.location.anchor.toPosition().computeLongestDistance(from) }
                .thenBy { it.location.id },
        ) ?: return null
        return chopLine(spot, spot.trees)
    }

    /** The one-line flow behind `::idle` with a location and optional tree names. */
    fun chopLine(locationId: String, treeNames: List<String>): String {
        val spot = spot(locationId)
        val trees = treeNames.map { tree(it, spot) }.toSet().ifEmpty { spot.trees }
        return chopLine(spot, trees)
    }

    private fun chopLine(spot: WoodcuttingSpot, trees: Set<Tree>): String =
        "chop ${trees.sortedBy { it.level }.joinToString(",") { it.name.lowercase() }} @${spot.location.id} drop"

    private fun resolve(step: FlowStep, at: Location?): ResolvedStep =
        when (step) {
            is FlowStep.Chop -> {
                val spot = spot(step.locationId)
                val trees = step.trees.mapTo(mutableSetOf()) { tree(it, spot) }
                ResolvedStep.Chop(spot, ChopAction(trees, dropWhenFull = step.drop), step.until)
            }
            FlowStep.BankDepositAll -> {
                if (at == null) throw FlowError("bank comes after a chop step, so the flow knows which bank to use")
                val booth = at.bank ?: throw FlowError("${at.name} has no bank")
                ResolvedStep.Bank(at, booth)
            }
            FlowStep.Loop -> ResolvedStep.Loop
        }

    private fun spot(locationId: String): WoodcuttingSpot =
        spots[locationId.lowercase()]
            ?: throw FlowError("Unknown location '$locationId'. Locations: ${locationIds.joinToString(", ")}")

    private fun tree(name: String, spot: WoodcuttingSpot): Tree {
        val tree = Tree.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw FlowError("'$name' is not a kind of tree")
        if (!spot.has(tree)) {
            val growing = spot.trees.sortedBy { it.level }.joinToString(", ") { it.name.lowercase() }
            throw FlowError("No $name trees at ${spot.location.name}, only $growing")
        }
        return tree
    }
}
