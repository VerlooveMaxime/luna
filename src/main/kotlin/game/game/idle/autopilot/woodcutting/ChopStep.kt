package game.idle.autopilot.woodcutting

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepField
import game.idle.flow.StepType
import game.idle.location.LocationCatalog
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.Position
import io.luna.game.model.mob.Player

/**
 * `chop <tree> @<location>`: one kind of tree at a location, until the inventory is full. Resolving every location
 * up front validates the data file at boot.
 */
class ChopStepType(catalog: LocationCatalog) : StepType {

    private val spots: Map<String, Map<Tree, WoodcuttingSpot>> =
        catalog.locations.associate { it.id to WoodcuttingSpot.from(it) }

    private val locationIds: List<String> = spots.keys.sorted()

    override val keyword = "chop"

    override val label = "chop"

    override val usage = "chop <tree> @<location>"

    override val fields = listOf(
        StepField("tree") { values -> treesAt(values[LOCATION]) },
        StepField("location") { locationIds },
    )

    override fun parse(words: List<String>): List<String> {
        val tree = words.firstOrNull() ?: throw FlowError("chop needs a tree: $usage")
        if (',' in tree) throw FlowError("One kind of tree per chop step: $usage")
        val at = words.getOrNull(1)
        if (at == null || !at.startsWith("@") || at.length == 1) throw FlowError("chop needs a location: chop $tree @<location>")
        val rest = words.drop(2)
        if (rest.isNotEmpty()) {
            throw FlowError(
                "Unexpected '${rest.joinToString(" ")}' after the location. A chop step ends when the inventory is full; " +
                    "'drop' and 'bank deposit all' are steps of their own, and the flow repeats by itself.",
            )
        }
        return listOf(tree, at.substring(1))
    }

    override fun line(values: List<String>): String = "chop ${values[TREE]} @${values[LOCATION]}"

    override fun resolve(values: List<String>, context: FlowContext): ResolvedStep {
        val spot = spot(values[TREE], spotsAt(values[LOCATION]))
        return ChopStep(spot, ChopAction(setOf(spot.tree)))
    }

    /** The chop line behind `::idle`: the spot nearest to [from], null when no location has trees. */
    fun nearestLine(from: Position): String? {
        val spot = spots.values.flatMap { it.values }.minWithOrNull(
            compareBy<WoodcuttingSpot> { it.area.anchor.toPosition().computeLongestDistance(from) }
                .thenBy { it.location.id }
                .thenBy { it.tree.level },
        ) ?: return null
        return line(spot)
    }

    /** The chop line behind `::idle` with a location and an optional tree name; without one, the easiest tree there. */
    fun lineAt(locationId: String, treeName: String?): String {
        val here = spotsAt(locationId)
        val spot = if (treeName == null) here.values.minBy { it.tree.level } else spot(treeName, here)
        return line(spot)
    }

    /** The kinds of tree at a location, easiest first; empty for an unknown location. */
    private fun treesAt(locationId: String): List<String> =
        spots[locationId.lowercase()]?.keys.orEmpty().sortedBy { it.level }.map { it.name.lowercase() }

    private fun line(spot: WoodcuttingSpot): String = line(listOf(spot.tree.name.lowercase(), spot.location.id))

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

    private companion object {
        const val TREE = 0
        const val LOCATION = 1
    }
}

/** A chop step resolved: the spot and what to cut there. Later steps know the location and that it gathers logs. */
data class ChopStep(val spot: WoodcuttingSpot, val action: ChopAction) : ResolvedStep {

    override fun after(context: FlowContext): FlowContext =
        context.copy(location = spot.location, gathered = context.gathered + spot.tree.logId)

    override fun activity(player: Player): StepActivity = WoodcuttingActivity(LunaWoodcutter(player, spot), action)
}
