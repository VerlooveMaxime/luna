package game.idle.autopilot.bank

import game.idle.autopilot.bank.BankDecision.Close
import game.idle.autopilot.bank.BankDecision.Deposit
import game.idle.autopilot.bank.BankDecision.Open
import game.idle.autopilot.bank.BankDecision.Settled
import game.idle.autopilot.bank.BankDecision.WalkToBooth
import game.idle.autopilot.bank.BankDecision.Withdraw
import game.idle.flow.Meter
import game.idle.flow.StepOutcome
import game.idle.flow.StepWork

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

    /** Breaks off the walk to the booth in progress. */
    fun interrupt()
}

/**
 * The bank step's work: carries out [BankPlanner] decisions for [order], remembering what the booth refused or did not
 * hand out. A deposit or a withdrawal that moved something is its work; the walk, opening, closing and a move that moved
 * nothing only approach it. It counts nothing.
 */
class BankWork(private val banker: Banker, private val order: BankOrder) : StepWork {

    private var tries = BankTries()

    override val meter: Meter = Meter.None

    override fun isBusy(): Boolean = banker.isBusy()

    override fun finishing(): Boolean = false

    override fun interrupt() = banker.interrupt()

    override fun act(left: Int?): StepOutcome {
        val view = banker.look()
        val plan = BankPlanner.plan(view, order, tries, banker::stacks)
        return carryOut(BankPlanner.decide(view, plan, tries.moved, order.ifStuck))
    }

    private fun carryOut(decision: BankDecision): StepOutcome =
        when (decision) {
            WalkToBooth -> approach(banker::walkToBooth)
            Open -> approach(banker::open)
            is Deposit -> deposited(banker.deposit(decision.slots))
            is Withdraw -> withdrew(banker.withdraw(decision.takes))
            Close -> approach(banker::close)
            is Settled -> decision.outcome
        }

    private fun approach(click: () -> Unit): StepOutcome {
        click()
        return StepOutcome.Approached
    }

    private fun deposited(move: BankMove): StepOutcome {
        tries = tries.deposited(move)
        return worked(move)
    }

    private fun withdrew(move: BankMove): StepOutcome {
        tries = tries.withdrew(move)
        return worked(move)
    }

    /** A bank refusing every lap must not count as work, or the lap guard would never stop the flow it holds up. */
    private fun worked(move: BankMove): StepOutcome = if (move.moved) StepOutcome.Worked else StepOutcome.Approached
}
