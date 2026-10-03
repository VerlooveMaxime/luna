package game.idle.autopilot.woodcutting

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepField
import game.idle.flow.StepType
import game.idle.flow.WorkSpot
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.woodcutting.cutTree.Tree
import game.skill.woodcutting.cutTree.TreeStump
import io.luna.game.model.mob.Player

/** `chop <tree> [within <n>]`: one kind of tree within n tiles of the work spot, until the inventory is full. */
object ChopStepType : StepType {

    const val DEFAULT_RADIUS = 10

    /** The radii the builder cycles through; a typed line may use any from 1 to [Area.MAX_RADIUS]. */
    val RADII = listOf(5, 10, 15, 20, 30)

    /** The kinds Luna has standing trees for, easiest first (teak and mahogany have none yet). */
    val CUTTABLE: List<Tree> = Tree.entries.filter { !TreeStump.ALIVE_TREE_MAP.get(it).isEmpty() }.sortedBy { it.level }

    override val keyword = "chop"

    override val label = "chop"

    override val usage = "chop <tree> [within <n>]"

    override val fields = listOf(
        StepField.Choice("tree") { CUTTABLE.map { it.name.lowercase() } },
        StepField.Choice("within (tiles)", default = DEFAULT_RADIUS.toString()) { RADII.map { it.toString() } },
    )

    override fun parse(words: List<String>): List<String> {
        val tree = words.firstOrNull() ?: throw FlowError("chop needs a tree: $usage")
        if (',' in tree) throw FlowError("One kind of tree per chop step: $usage")
        val rest = words.drop(1)
        if (rest.firstOrNull()?.startsWith("@") == true) {
            throw FlowError(
                "chop no longer takes a location: put a 'walk' step before it, or leave it out to chop around " +
                    "where you press Run",
            )
        }
        val radius = when {
            rest.isEmpty() -> DEFAULT_RADIUS
            rest.size == 2 && rest[0] == "within" -> radius(rest[1])
            else -> throw FlowError("Unexpected '${rest.joinToString(" ")}' after the tree: $usage")
        }
        return listOf(tree, radius.toString())
    }

    override fun line(values: List<String>): String {
        val radius = values[RADIUS]
        return if (radius == DEFAULT_RADIUS.toString()) "chop ${values[TREE]}" else "chop ${values[TREE]} within $radius"
    }

    override fun resolve(values: List<String>, context: FlowContext): ResolvedStep {
        val name = values[TREE]
        val tree = Tree.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw FlowError("'$name' is not a kind of tree")
        if (tree !in CUTTABLE) throw FlowError("There are no $name trees to cut in this world yet")
        return ChopStep(tree, radius(values[RADIUS]), context.workSpot)
    }

    private fun radius(text: String): Int {
        val radius = text.toIntOrNull()
        if (radius == null || radius !in 1..Area.MAX_RADIUS) {
            throw FlowError("within takes a number of tiles from 1 to ${Area.MAX_RADIUS}, not '$text'")
        }
        return radius
    }

    private const val TREE = 0
    private const val RADIUS = 1
}

/** A chop step resolved: what to cut, how far from the work spot. Later steps know it gathers logs. */
data class ChopStep(val tree: Tree, val radius: Int, val workSpot: WorkSpot) : ResolvedStep {

    override fun after(context: FlowContext): FlowContext = context.copy(gathered = context.gathered + tree.logId)

    override fun activity(player: Player, runTile: Tile): StepActivity {
        val spot = WoodcuttingSpot(tree, Area(workSpot.tile(runTile), radius))
        return WoodcuttingActivity(LunaWoodcutter(player, spot), ChopAction(setOf(tree)))
    }
}
