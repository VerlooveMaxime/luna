package game.idle.autopilot.bank

import game.idle.autopilot.bank.BankDecision.Blocked
import game.idle.autopilot.bank.BankDecision.Close
import game.idle.autopilot.bank.BankDecision.Deposit
import game.idle.autopilot.bank.BankDecision.Done
import game.idle.autopilot.bank.BankDecision.Open
import game.idle.autopilot.bank.BankDecision.WalkToBooth

/** What the bank autopilot knows when it decides. [depositableSlots] are inventory slots that should go in. */
data class BankView(
    val boothFound: Boolean,
    val boothUsableFromHere: Boolean,
    val bankOpen: Boolean,
    val depositableSlots: List<Int>,
)

sealed interface BankDecision {

    data object WalkToBooth : BankDecision

    data object Open : BankDecision

    data object Deposit : BankDecision

    data object Close : BankDecision

    data object Done : BankDecision

    data class Blocked(val reason: BankBlockedReason) : BankDecision
}

enum class BankBlockedReason(val message: String) {
    NO_BOOTH("Autopilot: there is no bank booth this step can use on this floor."),
}

/** Walk to the booth, open it, put everything in at once, close, done. */
object BankPlanner {

    fun decide(view: BankView): BankDecision =
        when {
            !view.boothFound -> Blocked(BankBlockedReason.NO_BOOTH)
            view.bankOpen && view.depositableSlots.isNotEmpty() -> Deposit
            view.bankOpen -> Close
            view.depositableSlots.isEmpty() -> Done
            view.boothUsableFromHere -> Open
            else -> WalkToBooth
        }
}
