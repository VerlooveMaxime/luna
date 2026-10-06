package game.idle.autopilot.woodcutting

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepRadius
import game.idle.flow.StepType
import game.idle.flow.WorkSpot
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.woodcutting.cutTree.Tree
import game.skill.woodcutting.cutTree.TreeStump
import io.luna.game.model.mob.Player

/**
 * `chop [<n>] <tree> [within <r>]`: one kind of tree within r tiles of the work spot, n logs or, without n, until the
 * inventory is full.
 */
object ChopStepType : StepType {

    /** The amount field's word for "until the inventory is full". */
    const val FULL = "full"

    /** The kinds Luna has standing trees for, easiest first (teak and mahogany have none yet). */
    val CUTTABLE: List<Tree> = Tree.entries.filter { !TreeStump.ALIVE_TREE_MAP.get(it).isEmpty() }.sortedBy { it.level }

    override val keyword = "chop"

    override val label = "chop"

    override val usage = "chop [<n>] <tree> [within <r>]"

    override val fields = listOf(
        StepField.Choice("tree") { CUTTABLE.map { it.name.lowercase() } },
        StepField.Choice("amount") { listOf(FULL) + StepAmount.COUNTS },
        StepRadius.field(),
    )

    override fun parse(words: List<String>): List<String> {
        val (count, afterCount) = StepAmount.split(words)
        val (tree, radius) = treeAndRadius(afterCount)
        return listOf(tree, StepAmount.value(count, FULL), radius.toString())
    }

    private fun treeAndRadius(words: List<String>): Pair<String, Int> {
        val tree = words.firstOrNull() ?: throw FlowError("chop needs a tree: $usage")
        if (',' in tree) throw FlowError("One kind of tree per chop step: $usage")
        val rest = words.drop(1)
        if (rest.firstOrNull()?.startsWith("@") == true) {
            throw FlowError(
                "chop no longer takes a location: put a 'walk' step before it, or leave it out to chop around " +
                    "where you press Run",
            )
        }
        return tree to StepRadius.parse(rest, after = "tree", usage)
    }

    override fun line(values: List<String>): String =
        "chop ${StepAmount.prefix(values[AMOUNT])}${values[TREE]}${StepRadius.suffix(values[RADIUS])}"

    override fun resolve(values: List<String>, context: FlowContext): ResolvedStep {
        val name = values[TREE]
        val tree = Tree.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw FlowError("'$name' is not a kind of tree")
        if (tree !in CUTTABLE) throw FlowError("There are no $name trees to cut in this world yet")
        return ChopStep(tree, StepRadius.check(values[RADIUS]), context.workSpot, StepAmount.count(values[AMOUNT]))
    }

    private const val TREE = 0
    private const val AMOUNT = 1
    private const val RADIUS = 2
}

/**
 * A chop step resolved: what to cut, how far from the work spot, and how many logs ([amount], null for a full
 * inventory). Later steps know it gathers logs.
 */
data class ChopStep(val tree: Tree, val radius: Int, val workSpot: WorkSpot, val amount: Int? = null) : ResolvedStep {

    override fun after(context: FlowContext): FlowContext = context.copy(gathered = context.gathered + tree.logId)

    override fun activity(player: Player, runTile: Tile): StepActivity {
        val spot = WoodcuttingSpot(tree, Area(workSpot.tile(runTile), radius))
        return WoodcuttingActivity(LunaWoodcutter(player, spot), ChopAction(setOf(tree)), amount)
    }
}
