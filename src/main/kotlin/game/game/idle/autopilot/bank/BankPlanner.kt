package game.idle.autopilot.bank

import game.idle.autopilot.bank.BankDecision.Blocked
import game.idle.autopilot.bank.BankDecision.Close
import game.idle.autopilot.bank.BankDecision.Deposit
import game.idle.autopilot.bank.BankDecision.Done
import game.idle.autopilot.bank.BankDecision.Open
import game.idle.autopilot.bank.BankDecision.Stuck
import game.idle.autopilot.bank.BankDecision.WalkToBooth
import game.idle.autopilot.bank.BankDecision.Withdraw

/** An item in a bag slot: its [id] and how many the slot holds. */
data class Held(val id: Int, val amount: Int)

/** What the bank autopilot sees: the booth, the bank window, the bag's slots (null for an empty one), the bank by id. */
data class BankView(
    val boothFound: Boolean,
    val boothUsableFromHere: Boolean,
    val bankOpen: Boolean,
    val bag: List<Held?>,
    val bank: Map<Int, Int>,
)

/** [amount] of item [id] to withdraw. */
data class Take(val id: Int, val amount: Int)

/** Why a bank step that had something to do can move nothing. */
sealed interface StuckReason {

    data class NoneLeft(val id: Int) : StuckReason

    data object NoRoom : StuckReason

    data object BankFull : StuckReason
}

/**
 * What a bank step can still do: the bag slots to [deposits] and the [takes], worked out as if the deposits were made
 * first; [stuck] says why it can move nothing although it had something to do, null when it can or had nothing to do.
 */
data class BankPlan(val deposits: List<Int>, val takes: List<Take>, val stuck: StuckReason?) {

    val canMove: Boolean get() = deposits.isNotEmpty() || takes.isNotEmpty()
}

/** What a bank step already tried at the booth: ids the bank [refused], withdrawals that got nothing, whether anything [moved]. */
data class BankTries(val refused: Set<Int> = emptySet(), val emptied: Set<Int> = emptySet(), val moved: Boolean = false) {

    /** These tries once [move] deposited: what failed is refused from now on. */
    fun deposited(move: BankMove): BankTries = copy(refused = refused + move.failed, moved = moved || move.moved)

    /** These tries once [move] withdrew: what failed is not asked for again. */
    fun withdrew(move: BankMove): BankTries = copy(emptied = emptied + move.failed, moved = moved || move.moved)
}

sealed interface BankDecision {

    data object WalkToBooth : BankDecision

    data object Open : BankDecision

    data class Deposit(val slots: List<Int>) : BankDecision

    data class Withdraw(val takes: List<Take>) : BankDecision

    data object Close : BankDecision

    data object Done : BankDecision

    data class Stuck(val reason: StuckReason) : BankDecision

    data class Blocked(val reason: BankBlockedReason) : BankDecision
}

enum class BankBlockedReason(val message: String) {
    NO_BOOTH("Autopilot: there is no bank booth this step can use on this floor."),
}

/**
 * Works out a bank step from what the bag and the bank hold, before walking (Maxime, 2026-10-10): nothing to do ends it
 * at once, something to do that nothing can answer is stuck, else walk, open, deposit, withdraw, close. A withdrawal
 * fills the bag up to its amount, or with as many as fit; what the bank refused or did not hand out is not tried again.
 */
object BankPlanner {

    /** [stacks] says whether an item id stacks in one slot. */
    fun plan(view: BankView, order: BankOrder, tries: BankTries, stacks: (Int) -> Boolean): BankPlan {
        val depositing = view.bag.withIndex().mapNotNull { (slot, held) -> held?.takeIf { order.deposit.takes(it.id) }?.let { slot to it.id } }
        val deposits = depositing.filter { (_, id) -> id !in tries.refused }.map { (slot, _) -> slot }
        val leaving = depositing.map { (slot, _) -> slot }.toSet()
        val kept = view.bag.filterIndexed { slot, _ -> slot !in leaving }.filterNotNull()
        val bag = kept.groupingBy { it.id }.fold(0) { total, held -> total + held.amount }.toMutableMap()
        var free = view.bag.size - kept.size
        val takes = mutableListOf<Take>()
        var short: StuckReason? = null
        var wanted = false
        for (withdrawal in order.withdrawals) {
            val inBag = bag[withdrawal.id] ?: 0
            val stackable = stacks(withdrawal.id)
            val room = if (!stackable) free else if (inBag > 0 || free > 0) Int.MAX_VALUE - inBag else 0
            val want = withdrawal.amount?.let { it - inBag } ?: room
            if (want <= 0) continue
            wanted = true
            val inBank = if (withdrawal.id in tries.emptied) 0 else view.bank[withdrawal.id] ?: 0
            val take = minOf(want, room, inBank)
            if (take == 0) {
                short = short ?: if (room == 0) StuckReason.NoRoom else StuckReason.NoneLeft(withdrawal.id)
                continue
            }
            takes += Take(withdrawal.id, take)
            free -= if (!stackable) take else if (inBag == 0) 1 else 0
            bag[withdrawal.id] = inBag + take
        }
        val stuck = when {
            deposits.isNotEmpty() || takes.isNotEmpty() -> null
            depositing.isNotEmpty() -> StuckReason.BankFull
            wanted -> short
            else -> null
        }
        return BankPlan(deposits, takes, stuck)
    }

    fun decide(view: BankView, plan: BankPlan, moved: Boolean): BankDecision =
        when {
            view.bankOpen && plan.deposits.isNotEmpty() -> Deposit(plan.deposits)
            view.bankOpen && plan.takes.isNotEmpty() -> Withdraw(plan.takes)
            view.bankOpen -> Close
            plan.canMove && !view.boothFound -> Blocked(BankBlockedReason.NO_BOOTH)
            plan.canMove && view.boothUsableFromHere -> Open
            plan.canMove -> WalkToBooth
            plan.stuck == null || moved -> Done
            else -> Stuck(plan.stuck)
        }
}
