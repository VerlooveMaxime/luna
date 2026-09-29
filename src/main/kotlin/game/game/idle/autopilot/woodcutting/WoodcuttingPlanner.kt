package game.idle.autopilot.woodcutting

import game.idle.autopilot.woodcutting.WoodcuttingDecision.Blocked
import game.idle.autopilot.woodcutting.WoodcuttingDecision.Chop
import game.idle.autopilot.woodcutting.WoodcuttingDecision.DropLogs
import game.idle.autopilot.woodcutting.WoodcuttingDecision.WalkTo
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.Position

/** A standing tree in sight. [usableFromHere] means the player can chop it without moving. */
data class TreeCandidate(
    val objectId: Int,
    val position: Position,
    val tree: Tree,
    val distance: Int,
    val usableFromHere: Boolean,
)

/** What the woodcutting autopilot knows about the player when it decides. */
data class WoodcuttingView(
    val woodcuttingLevel: Int,
    val hasUsableAxe: Boolean,
    val inventoryFull: Boolean,
    val logsInInventory: Int,
    val trees: List<TreeCandidate>,
)

sealed interface WoodcuttingDecision {

    /** A decision aimed at one tree. */
    sealed interface OnTree : WoodcuttingDecision {
        val tree: TreeCandidate
    }

    data class Chop(override val tree: TreeCandidate) : OnTree

    data class WalkTo(override val tree: TreeCandidate) : OnTree

    data object DropLogs : WoodcuttingDecision

    data class Blocked(val reason: BlockedReason) : WoodcuttingDecision
}

enum class BlockedReason(val message: String) {
    NO_AXE("Autopilot: you need an axe that you have the Woodcutting level to use."),
    INVENTORY_FULL("Autopilot: your inventory is full and there are no logs to drop."),
    NO_TREE("Autopilot: there is no tree you can cut nearby."),
}

/**
 * Power-chopping: cut the highest-level tree the player can, drop every log once the inventory is full.
 */
object WoodcuttingPlanner {

    private val preferredFirst: Comparator<TreeCandidate> =
        compareByDescending<TreeCandidate> { it.tree.level }
            .thenByDescending { it.usableFromHere }
            .thenBy { it.distance }
            .thenBy { it.position.x }
            .thenBy { it.position.y }

    fun decide(view: WoodcuttingView): WoodcuttingDecision {
        val best = view.trees.filter { it.tree.level <= view.woodcuttingLevel }.minWithOrNull(preferredFirst)
        return when {
            !view.hasUsableAxe -> Blocked(BlockedReason.NO_AXE)
            view.inventoryFull && view.logsInInventory > 0 -> DropLogs
            view.inventoryFull -> Blocked(BlockedReason.INVENTORY_FULL)
            best == null -> Blocked(BlockedReason.NO_TREE)
            best.usableFromHere -> Chop(best)
            else -> WalkTo(best)
        }
    }
}
