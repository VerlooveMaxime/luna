package game.idle.autopilot.woodcutting

import game.idle.autopilot.woodcutting.WoodcuttingDecision.Blocked
import game.idle.autopilot.woodcutting.WoodcuttingDecision.Chop
import game.idle.autopilot.woodcutting.WoodcuttingDecision.WalkTo
import game.idle.autopilot.woodcutting.WoodcuttingDecision.WalkToLocation
import game.idle.flow.Reason
import game.idle.flow.Target
import game.idle.flow.ToolNeed
import game.skill.woodcutting.cutTree.Axe
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.Position
import io.luna.game.model.mob.Skill

/** What the player asked to chop. */
data class ChopAction(val trees: Set<Tree>) {
    init {
        require(trees.isNotEmpty()) { "An action needs at least one kind of tree" }
    }
}

/**
 * A standing tree the player can walk to. [distance] is in walking steps and [approach] the tile those steps lead
 * to; [usableFromHere] means the player can chop it without moving.
 */
data class TreeCandidate(
    val objectId: Int,
    val position: Position,
    val tree: Tree,
    val distance: Int,
    val usableFromHere: Boolean,
    val approach: Position,
)

/** What the woodcutting autopilot knows about the player when it decides. */
data class WoodcuttingView(
    val woodcuttingLevel: Int,
    val hasUsableAxe: Boolean,
    val inventoryFull: Boolean,
    val logsInInventory: Int,
    val atLocation: Boolean,
    val trees: List<TreeCandidate>,
)

sealed interface WoodcuttingDecision {

    /** A decision aimed at one tree. */
    sealed interface OnTree : WoodcuttingDecision {
        val tree: TreeCandidate
    }

    data class Chop(override val tree: TreeCandidate) : OnTree

    data class WalkTo(override val tree: TreeCandidate) : OnTree

    data object WalkToLocation : WoodcuttingDecision

    data class Blocked(val reason: Reason) : WoodcuttingDecision
}

/**
 * Cut the highest-level wanted tree the player can, and walk back to the work spot when nothing is in reach from
 * outside it. A full inventory is the chop step's end ([WoodcuttingActivity]); here it only blocks.
 */
object WoodcuttingPlanner {

    /** Any axe, each with the Woodcutting level it takes; what the step needs and blocks on. */
    val AXE = ToolNeed("axe", Axe.entries.associate { it.id to it.level }, Skill.WOODCUTTING)

    private val preferredFirst: Comparator<TreeCandidate> =
        compareByDescending<TreeCandidate> { it.tree.level }
            .thenByDescending { it.usableFromHere }
            .thenBy { it.distance }
            .thenBy { it.position.x }
            .thenBy { it.position.y }

    fun decide(view: WoodcuttingView, action: ChopAction): WoodcuttingDecision {
        val best = view.trees
            .filter { it.tree in action.trees && it.tree.level <= view.woodcuttingLevel }
            .minWithOrNull(preferredFirst)
        return when {
            !view.hasUsableAxe -> Blocked(Reason.NeedsTool(AXE))
            view.inventoryFull -> Blocked(Reason.BagFull(emptyList()))
            best == null && !view.atLocation -> WalkToLocation
            best == null -> Blocked(Reason.NoneInReach(Target.TREE))
            best.usableFromHere -> Chop(best)
            else -> WalkTo(best)
        }
    }
}
