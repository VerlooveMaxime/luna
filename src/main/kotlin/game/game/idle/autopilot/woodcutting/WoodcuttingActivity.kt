package game.idle.autopilot.woodcutting

import game.idle.autopilot.woodcutting.WoodcuttingDecision.Blocked
import game.idle.autopilot.woodcutting.WoodcuttingDecision.Chop
import game.idle.autopilot.woodcutting.WoodcuttingDecision.DropLogs
import game.idle.autopilot.woodcutting.WoodcuttingDecision.OnTree
import game.idle.autopilot.woodcutting.WoodcuttingDecision.WalkTo
import game.idle.autopilot.woodcutting.WoodcuttingDecision.WalkToLocation
import game.idle.flow.StepActivity
import io.luna.game.model.Position

/** What the woodcutting autopilot can see and do for one player. [LunaWoodcutter] is the in-game one. */
interface Woodcutter {

    fun isBusy(): Boolean

    fun look(): WoodcuttingView

    fun chop(tree: TreeCandidate)

    fun walkTo(tree: TreeCandidate)

    fun walkToLocation()

    fun dropLogs()

    fun tell(message: String)
}

/**
 * Carries out [WoodcuttingPlanner] decisions for one [action]. A tree that gets the same decision twice in a row
 * (walked to but still out of reach, or chopped without the tree falling or the inventory filling) is skipped for
 * as long as this activity runs, so an unreachable tree cannot trap the player.
 */
class WoodcuttingActivity(private val woodcutter: Woodcutter, private val action: ChopAction) : StepActivity {

    private val skippedTrees = mutableSetOf<Position>()
    private var lastDecision: WoodcuttingDecision? = null

    override fun isBusy(): Boolean = woodcutter.isBusy()

    /** Chopping has no end of its own; the flow's `until` decides. */
    override fun isDone(): Boolean = false

    override fun act() {
        val decision = decideSkippingRetries(woodcutter.look())
        when (decision) {
            is Chop -> woodcutter.chop(decision.tree)
            is WalkTo -> woodcutter.walkTo(decision.tree)
            WalkToLocation -> woodcutter.walkToLocation()
            DropLogs -> woodcutter.dropLogs()
            is Blocked -> if (decision != lastDecision) woodcutter.tell(decision.reason.message)
        }
        lastDecision = decision
    }

    private fun decideSkippingRetries(view: WoodcuttingView): WoodcuttingDecision {
        val decision = decide(view)
        val retried = retriedTree(decision) ?: return decision
        skippedTrees += retried.position
        return decide(view)
    }

    private fun decide(view: WoodcuttingView): WoodcuttingDecision =
        WoodcuttingPlanner.decide(view.copy(trees = view.trees.filterNot { it.position in skippedTrees }), action)

    /** The tree [decision] aims at when the previous decision was the same step on the same tree. */
    private fun retriedTree(decision: WoodcuttingDecision): TreeCandidate? {
        val attempt = decision as? OnTree ?: return null
        val last = lastDecision as? OnTree ?: return null
        return attempt.tree.takeIf { attempt::class == last::class && it.position == last.tree.position }
    }
}
