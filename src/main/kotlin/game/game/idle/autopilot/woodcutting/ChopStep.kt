package game.idle.autopilot.woodcutting

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepRadius
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.WorkSpot
import game.idle.flow.option.GameNames
import game.idle.flow.option.StepTarget
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.woodcutting.cutTree.Tree
import game.skill.woodcutting.cutTree.TreeStump
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/** Chop: one kind of tree within a radius of the work spot, a count of logs or, without one, until the inventory is full. */
object ChopStepType : StepType {

    const val TREE = "tree"

    /** The kinds Luna has standing trees for, easiest first (teak and mahogany have none yet). */
    val CUTTABLE: List<Tree> = Tree.entries.filter { !TreeStump.ALIVE_TREE_MAP.get(it).isEmpty() }.sortedBy { it.level }

    override val kind = "chop"

    override val label = "chop"

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Skill(Skill.WOODCUTTING)

    override fun target(names: GameNames): StepTarget = StepTarget(TREE, TreeOptions(names))

    override fun details(settings: StepSettings, context: FlowContext): List<String> =
        listOf(StepAmount.detail(settings, unbounded = "until the bag is full"))

    override val fields = listOf(
        StepField.Choice(TREE, "tree") { CUTTABLE.map { it.name.lowercase() } },
        StepAmount.field(unbounded = "full"),
        StepRadius.field(),
    )

    override fun summary(settings: StepSettings): String =
        "chop ${StepAmount.prefix(settings)}${settings[TREE] ?: "?"}${StepRadius.suffix(settings)}"

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val name = settings[TREE] ?: throw FlowError("chop needs a tree")
        val tree = Tree.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: throw FlowError("'$name' is not a kind of tree")
        if (tree !in CUTTABLE) throw FlowError("There are no $name trees to cut in this world yet")
        return ChopStep(tree, StepRadius.read(settings), context.workSpot, StepAmount.read(settings))
    }
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
