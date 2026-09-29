package game.idle.autopilot.bank

import game.idle.autopilot.bank.BankDecision.Blocked
import game.idle.autopilot.bank.BankDecision.Close
import game.idle.autopilot.bank.BankDecision.Deposit
import game.idle.autopilot.bank.BankDecision.Done
import game.idle.autopilot.bank.BankDecision.Open
import game.idle.autopilot.bank.BankDecision.WalkToBooth
import game.idle.flow.StepActivity

/** What the bank autopilot can see and do for one player. [LunaBanker] is the in-game one. */
interface Banker {

    fun isBusy(): Boolean

    fun look(): BankView

    fun walkToBooth()

    fun open()

    fun deposit(slots: List<Int>)

    fun close()

    fun tell(message: String)
}

/** Carries out [BankPlanner] decisions; the step is over once the planner says [Done]. */
class BankActivity(private val banker: Banker) : StepActivity {

    private var lastDecision: BankDecision? = null

    override fun isBusy(): Boolean = banker.isBusy()

    override fun isDone(): Boolean = lastDecision == Done

    override fun act() {
        val view = banker.look()
        val decision = BankPlanner.decide(view)
        when (decision) {
            WalkToBooth -> banker.walkToBooth()
            Open -> banker.open()
            Deposit -> banker.deposit(view.depositableSlots)
            Close -> banker.close()
            Done -> Unit
            is Blocked -> if (decision != lastDecision) banker.tell(decision.reason.message)
        }
        lastDecision = decision
    }
}
