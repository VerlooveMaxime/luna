package game.idle.autopilot.woodcutting

import game.idle.autopilot.woodcutting.WoodcuttingDecision.Blocked
import game.idle.autopilot.woodcutting.WoodcuttingDecision.Chop
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

    /** How many logs of the step's kind the player carries. */
    fun logs(): Int

    /** Stops the chop in progress. */
    fun stop()

    fun tell(message: String)
}

/**
 * Carries out [WoodcuttingPlanner] decisions for one [action]. The step is over once [amount] logs were cut, or,
 * without an amount, once the inventory fills up after at least one chop; an inventory that is already full when the
 * step starts blocks it instead, since no later step would empty it. A chop that reaches the amount is stopped at
 * once: the step stops being busy so it can act. A tree that gets the same decision twice in a row (walked to but
 * still out of reach, or chopped without the tree falling or the inventory filling) is skipped for as long as this
 * activity runs, so an unreachable tree cannot trap the player.
 */
class WoodcuttingActivity(
    private val woodcutter: Woodcutter,
    private val action: ChopAction,
    private val amount: Int? = null,
) : StepActivity {

    private val skippedTrees = mutableSetOf<Position>()
    private var lastDecision: WoodcuttingDecision? = null
    private var logsAtStart: Int? = null
    private var chopped = false
    private var done = false

    override fun isBusy(): Boolean = woodcutter.isBusy() && !amountReached()

    override fun isDone(): Boolean = done

    override fun act() {
        if (logsAtStart == null) logsAtStart = woodcutter.logs()
        if (amountReached()) {
            woodcutter.stop()
            done = true
            return
        }
        val view = woodcutter.look()
        if (chopped && view.inventoryFull) {
            done = true
            return
        }
        val decision = decideSkippingRetries(view)
        carryOut(decision)
        lastDecision = decision
    }

    private fun amountReached(): Boolean {
        val start = logsAtStart ?: return false
        return amount != null && woodcutter.logs() - start >= amount
    }

    private fun carryOut(decision: WoodcuttingDecision) = when (decision) {
        is Chop -> chop(decision.tree)
        is WalkTo -> woodcutter.walkTo(decision.tree)
        WalkToLocation -> woodcutter.walkToLocation()
        is Blocked -> tellOnce(decision)
    }

    private fun chop(tree: TreeCandidate) {
        chopped = true
        woodcutter.chop(tree)
    }

    private fun tellOnce(decision: Blocked) {
        if (decision != lastDecision) woodcutter.tell(decision.reason.message)
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
