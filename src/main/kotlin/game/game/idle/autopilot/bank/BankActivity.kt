package game.idle.autopilot.bank

import game.idle.autopilot.bank.BankDecision.Blocked
import game.idle.autopilot.bank.BankDecision.Close
import game.idle.autopilot.bank.BankDecision.Deposit
import game.idle.autopilot.bank.BankDecision.Done
import game.idle.autopilot.bank.BankDecision.Open
import game.idle.autopilot.bank.BankDecision.Stuck
import game.idle.autopilot.bank.BankDecision.WalkToBooth
import game.idle.autopilot.bank.BankDecision.Withdraw
import game.idle.flow.StepActivity
import game.idle.flow.option.GameNames

/** What a deposit or withdrawal at the bank did: whether anything [moved], and the item ids that [failed]. */
data class BankMove(val moved: Boolean, val failed: Set<Int>)

/** What the bank autopilot can see and do for one player. [LunaBanker] is the in-game one. */
interface Banker {

    fun isBusy(): Boolean

    fun look(): BankView

    /** Whether item [id] stacks in one bag slot. */
    fun stacks(id: Int): Boolean

    fun walkToBooth()

    fun open()

    /** Deposits the items in the bag's [slots]; an item the bank has no room for fails. */
    fun deposit(slots: List<Int>): BankMove

    /** Withdraws [takes] in order, unnoted; one the bank hands out none of fails. */
    fun withdraw(takes: List<Take>): BankMove

    fun close()

    /** Says [message] in the chat box. */
    fun tell(message: String)
}

/**
 * Carries out [BankPlanner] decisions for [order]; the step is over once the planner says [Done], or when it is stuck and
 * skips (Maxime, S01). Stuck with "Stop the flow", it stops the flow. Items are named with [names].
 */
class BankActivity(private val banker: Banker, private val order: BankOrder, private val names: GameNames) : StepActivity {

    private var lastDecision: BankDecision? = null
    private var tries = BankTries()
    private var stopReason: String? = null

    override fun isBusy(): Boolean = banker.isBusy()

    override fun isDone(): Boolean = lastDecision == Done || (lastDecision is Stuck && order.ifStuck == IfStuck.SKIP)

    override fun blocked(): String? = (lastDecision as? Blocked)?.reason?.message

    override fun stopReason(): String? = stopReason

    override fun act() {
        val view = banker.look()
        val decision = BankPlanner.decide(view, BankPlanner.plan(view, order, tries, banker::stacks), tries.moved)
        carryOut(decision)
        lastDecision = decision
    }

    private fun carryOut(decision: BankDecision) = when (decision) {
        // Nothing to do on a block (the autopilot tells it) or once done; as the last arm an empty body would leave
        // JaCoCo a branch no test can reach, so these come first.
        is Blocked -> Unit
        Done -> Unit
        WalkToBooth -> banker.walkToBooth()
        Open -> banker.open()
        is Deposit -> tries.deposited(banker.deposit(decision.slots)).let { tries = it }
        is Withdraw -> tries.withdrew(banker.withdraw(decision.takes)).let { tries = it }
        Close -> banker.close()
        is Stuck -> stuck(decision.reason)
    }

    private fun stuck(reason: StuckReason) {
        val why = words(reason)
        if (order.ifStuck == IfStuck.STOP) {
            stopReason = "Autopilot: stopped, the bank step is stuck: $why."
        } else {
            banker.tell("Autopilot: the bank step is stuck ($why): skipped this lap.")
        }
    }

    private fun words(reason: StuckReason): String =
        when (reason) {
            is StuckReason.NoneLeft -> "no ${names.item(reason.id).lowercase()} left in the bank"
            StuckReason.NoRoom -> "no room in the bag"
            StuckReason.BankFull -> "the bank is full"
        }
}
