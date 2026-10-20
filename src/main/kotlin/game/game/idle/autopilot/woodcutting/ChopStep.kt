package game.idle.autopilot.woodcutting

import game.idle.autopilot.travel.walkUpsFor
import game.idle.flow.Counts
import game.idle.flow.Gathers
import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.LevelNeed
import game.idle.flow.OwnSpot
import game.idle.flow.Reasons
import game.idle.flow.ResolvedStep
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepNeeds
import game.idle.flow.StepRadius
import game.idle.flow.StepSettings
import game.idle.flow.StepSpot
import game.idle.flow.StepType
import game.idle.flow.StepWork
import game.idle.flow.ToolNeed
import game.idle.flow.WorkSpot
import game.idle.flow.option.GameNames
import game.idle.flow.option.StepTarget
import game.idle.location.Area
import game.idle.location.Tile
import game.idle.movement.TravelWebs
import game.skill.woodcutting.cutTree.Axe
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/** Chop: one kind of tree within a radius of the work spot, a count of logs or, without one, until the inventory is full. */
object ChopStepType : StepType {

    const val TREE = "tree"

    /** Every kind of tree Luna cuts, easiest first. */
    val CUTTABLE: List<Tree> = Tree.entries.sortedBy { it.level }

    /** Any axe, each with the Woodcutting level it takes; Luna's woodcutting takes one carried or wielded. */
    val AXE = ToolNeed("axe", Axe.entries.associate { it.id to it.level }, Skill.WOODCUTTING, worn = true)

    override val kind = "chop"

    override val label = "chop"

    override val description = "Cuts one kind of tree around the work spot."

    override val goal = true

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Skill(Skill.WOODCUTTING)

    override fun skill(settings: StepSettings): Int = Skill.WOODCUTTING

    override fun target(names: GameNames): StepTarget = StepTarget(TREE, TreeOptions(names))

    override fun details(settings: StepSettings, context: FlowContext): List<String> =
        listOf(StepAmount.detail(settings, unbounded = "until the bag is full"))

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            StepField.Search("Tree", target(names), "Which tree would you like to cut?"),
            StepAmount.field(StepAmount.LABEL, unbounded = "until the bag is full", button = "Full"),
            StepSpot.field(),
            StepRadius.field(),
        )

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val name = settings[TREE] ?: throw FlowError(Reasons.needs("Tree"))
        val tree = CUTTABLE.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: throw FlowError(Reasons.notA("Tree", name))
        return ChopStep(tree, StepRadius.read(settings), StepSpot.of(settings, context), StepAmount.read(settings))
    }
}

/**
 * A chop step resolved: what to cut, how far from the work spot, and how many logs ([amount], null for a full
 * inventory). Later steps know it gathers logs.
 */
data class ChopStep(
    val tree: Tree,
    val radius: Int,
    val workSpot: WorkSpot,
    override val amount: Int? = null,
) : ResolvedStep, OwnSpot, Gathers, Counts {

    override val ownArea: Area? get() = workSpot.ownArea(radius)

    override val fills: Set<Int> get() = setOf(tree.logId)

    override fun after(context: FlowContext): FlowContext = workSpot.after(context.copy(gathered = context.gathered + fills))

    /** An axe the player's Woodcutting level allows, and the tree's level. */
    override fun needs(): List<StepNeeds> =
        listOf(StepNeeds(tools = listOf(ChopStepType.AXE), level = LevelNeed(Skill.WOODCUTTING, tree.level)))

    override fun work(player: Player, runTile: Tile): StepWork {
        val area = Area(workSpot.tile(runTile), radius)
        return WoodcuttingWork(LunaWoodcutter(player, tree, area, TravelWebs.SERVER.doorwaysOf(player.world)), walkUpsFor(player))
    }
}
