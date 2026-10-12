package game.idle.autopilot.bank

/** A bank step's order that deposits the whole bag and withdraws nothing, skipping itself when stuck. */
val EVERYTHING = BankOrder(DepositRule.Everything, emptyList(), IfStuck.SKIP)

/**
 * A banker over a bag and a bank it moves items between, recording each step as text ("open", "deposit [0, 1]"): a
 * deposit empties the given slots unless [bankFull]; a withdrawal fills free bag slots, one item each, with what the bank
 * holds and takes it off, unless [handsOut] is false, when it hands out nothing.
 */
class FakeBanker(var view: BankView) : Banker {

    var busy = false
    var bankFull = false
    var handsOut = true
    val steps = mutableListOf<String>()

    override fun isBusy() = busy

    override fun look() = view

    override fun stacks(id: Int) = false

    override fun walkToBooth() {
        steps += "walk"
    }

    override fun open() {
        steps += "open"
        view = view.copy(bankOpen = true)
    }

    override fun deposit(slots: List<Int>): BankMove {
        steps += "deposit $slots"
        val ids = slots.mapNotNull { view.bag[it]?.id }.toSet()
        if (bankFull) return BankMove(moved = false, failed = ids)
        view = view.copy(bag = view.bag.mapIndexed { slot, held -> if (slot in slots) null else held })
        return BankMove(moved = true, failed = emptySet())
    }

    override fun withdraw(takes: List<Take>): BankMove {
        steps += "withdraw $takes"
        val failed = takes.filterNot(::handOut).map { it.id }.toSet()
        return BankMove(moved = failed.size < takes.size, failed)
    }

    /** Whether [take] got anything out of the bank into the bag. */
    private fun handOut(take: Take): Boolean {
        val free = view.bag.indices.filter { view.bag[it] == null }
        val count = minOf(take.amount, view.bank[take.id] ?: 0, free.size)
        if (!handsOut || count == 0) return false
        val filled = free.take(count).toSet()
        view = view.copy(
            bag = view.bag.mapIndexed { slot, held -> if (slot in filled) Held(take.id, 1) else held },
            bank = view.bank + (take.id to (view.bank.getValue(take.id) - count)),
        )
        return true
    }

    override fun close() {
        steps += "close"
        view = view.copy(bankOpen = false)
    }

    override fun interrupt() {
        steps += "interrupt"
    }
}
