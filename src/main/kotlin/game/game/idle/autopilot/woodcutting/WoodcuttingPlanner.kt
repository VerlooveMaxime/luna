package game.idle.autopilot.woodcutting

import game.idle.autopilot.woodcutting.WoodcuttingDecision.Chop
import game.idle.autopilot.woodcutting.WoodcuttingDecision.Settled
import game.idle.autopilot.woodcutting.WoodcuttingDecision.WalkTo
import game.idle.autopilot.woodcutting.WoodcuttingDecision.WalkToLocation
import game.idle.flow.Aim
import game.idle.flow.Aimed
import game.idle.flow.StepOutcome
import game.idle.flow.Target
import game.idle.location.Tile
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.Position

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
) {
    val aim: Aim get() = Aim(objectId, Tile.of(position))
}

/** What the chop step sees when it decides: whether the player is inside the step's area, and the trees in reach. */
data class WoodcuttingView(val atLocation: Boolean, val trees: List<TreeCandidate>)

sealed interface WoodcuttingDecision {

    /** A decision aimed at one tree. */
    sealed interface OnTree : WoodcuttingDecision, Aimed {
        val tree: TreeCandidate

        override val aim: Aim get() = tree.aim

        override val distance: Int get() = tree.distance
    }

    data class Chop(override val tree: TreeCandidate) : OnTree

    data class WalkTo(override val tree: TreeCandidate) : OnTree

    data object WalkToLocation : WoodcuttingDecision

    /** A decision that is one of the outcomes every kind shares. */
    data class Settled(val outcome: StepOutcome) : WoodcuttingDecision
}

/**
 * Cut the highest-level wanted tree in reach, leaving out the trees [game.idle.flow.RetrySkip] set aside, and walk back to the work
 * spot when nothing is in reach from outside it. The axe, the level and a full bag are the shared turn's to check
 * ([game.idle.flow.StepTurn]); with every tree a stump the step waits for one to grow back (Maxime, 2026-10-11).
 */
object WoodcuttingPlanner {

    private val preferredFirst: Comparator<TreeCandidate> =
        compareByDescending<TreeCandidate> { it.tree.level }
            .thenByDescending { it.usableFromHere }
            .thenBy { it.distance }
            .thenBy { it.position.x }
            .thenBy { it.position.y }

    fun decide(view: WoodcuttingView, action: ChopAction, skipped: Set<Aim>): WoodcuttingDecision {
        val wanted = view.trees.filter { it.tree in action.trees }
        val best = wanted.filterNot { it.aim in skipped }.minWithOrNull(preferredFirst)
        return when {
            best == null && !view.atLocation -> WalkToLocation
            best == null -> Settled(StepOutcome.noneInReach(Target.TREE, setAside = wanted.isNotEmpty()))
            best.usableFromHere -> Chop(best)
            else -> WalkTo(best)
        }
    }
}
