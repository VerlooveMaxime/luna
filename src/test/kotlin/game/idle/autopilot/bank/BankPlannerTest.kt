package game.idle.autopilot.bank

import game.idle.autopilot.bank.BankDecision.Blocked
import game.idle.autopilot.bank.BankDecision.Close
import game.idle.autopilot.bank.BankDecision.Deposit
import game.idle.autopilot.bank.BankDecision.Done
import game.idle.autopilot.bank.BankDecision.Open
import game.idle.autopilot.bank.BankDecision.Stuck
import game.idle.autopilot.bank.BankDecision.WalkToBooth
import game.idle.autopilot.bank.BankDecision.Withdraw
import game.idle.flow.StepItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BankPlannerTest {

    private val logs = 1511
    private val oak = 1521
    private val axe = 1351
    private val coins = 995
    private val feathers = 314

    private val stacks = { id: Int -> id == coins || id == feathers }

    /** A bag of 28 slots holding [held] first, then empty slots. */
    private fun bag(vararg held: Held?): List<Held?> = held.toList() + List(28 - held.size) { null }

    private fun view(bag: List<Held?> = bag(), bank: Map<Int, Int> = emptyMap(), open: Boolean = false) =
        BankView(boothFound = true, boothUsableFromHere = false, bankOpen = open, bag = bag, bank = bank)

    private fun order(deposit: DepositRule = DepositRule.Only(emptySet()), vararg withdrawals: StepItem) =
        BankOrder(deposit, withdrawals.toList(), IfStuck.SKIP)

    private fun plan(view: BankView, order: BankOrder, tries: BankTries = BankTries()) = BankPlanner.plan(view, order, tries, stacks)

    private fun logsTimes(count: Int): Array<Held?> = Array(count) { Held(logs, 1) }

    @Test
    fun `everything deposits every slot of the bag, tools too`() {
        val plan = plan(view(bag(Held(axe, 1), null, Held(logs, 1))), order(DepositRule.Everything))

        assertEquals(listOf(0, 2), plan.deposits)
    }

    @Test
    fun `a chosen deposit takes only the items it names`() {
        val plan = plan(view(bag(Held(axe, 1), Held(logs, 1), Held(coins, 5))), order(DepositRule.Only(setOf(logs, coins))))

        assertEquals(listOf(1, 2), plan.deposits)
    }

    @Test
    fun `an item the bank refused is not deposited again`() {
        val plan = plan(view(bag(Held(logs, 1), Held(oak, 1))), order(DepositRule.Everything), BankTries(refused = setOf(logs)))

        assertEquals(listOf(1), plan.deposits)
    }

    @Test
    fun `a withdrawal fills the bag up to its amount`() {
        val plan = plan(view(bag(*logsTimes(9)), bank = mapOf(logs to 50)), order(withdrawals = arrayOf(StepItem(logs, 14))))

        assertEquals(listOf(Take(logs, 5)), plan.takes)
    }

    @Test
    fun `a withdrawal already in the bag leaves nothing to do`() {
        val plan = plan(view(bag(*logsTimes(14)), bank = mapOf(logs to 50)), order(withdrawals = arrayOf(StepItem(logs, 14))))

        assertEquals(BankPlan(emptyList(), emptyList(), stuck = null), plan)
    }

    @Test
    fun `an empty bag and no withdrawal leave nothing to do`() {
        assertEquals(BankPlan(emptyList(), emptyList(), stuck = null), plan(view(), order(DepositRule.Everything)))
    }

    @Test
    fun `a withdrawal of all takes as many as the free slots hold`() {
        val plan = plan(view(bag(*logsTimes(20)), bank = mapOf(oak to 100)), order(withdrawals = arrayOf(StepItem(oak))))

        assertEquals(listOf(Take(oak, 8)), plan.takes)
    }

    @Test
    fun `a withdrawal counts the slots the deposits free`() {
        val plan = plan(view(bag(*logsTimes(28)), bank = mapOf(oak to 100)), order(DepositRule.Only(setOf(logs)), StepItem(oak)))

        assertEquals(listOf(Take(oak, 28)), plan.takes)
    }

    @Test
    fun `a withdrawal of all with the bag full has nothing to do`() {
        val plan = plan(view(bag(*logsTimes(28)), bank = mapOf(oak to 100)), order(withdrawals = arrayOf(StepItem(oak))))

        assertNull(plan.stuck)
    }

    @Test
    fun `a withdrawal takes at most what the bank holds`() {
        val plan = plan(view(bank = mapOf(oak to 3)), order(withdrawals = arrayOf(StepItem(oak, 14))))

        assertEquals(listOf(Take(oak, 3)), plan.takes)
    }

    @Test
    fun `a later withdrawal gets the slots the earlier ones leave`() {
        val plan = plan(view(bag(*logsTimes(20)), bank = mapOf(oak to 5, logs to 50)), order(withdrawals = arrayOf(StepItem(oak), StepItem(logs, 25))))

        assertEquals(listOf(Take(oak, 5), Take(logs, 3)), plan.takes)
    }

    @Test
    fun `a stack withdrawn into the bag takes one slot from the withdrawals after it`() {
        val plan = plan(view(bag(*logsTimes(26)), bank = mapOf(coins to 100, oak to 50)), order(withdrawals = arrayOf(StepItem(coins), StepItem(oak))))

        assertEquals(listOf(Take(coins, 100), Take(oak, 1)), plan.takes)
    }

    @Test
    fun `a stack the bag already holds needs no free slot`() {
        val plan = plan(view(bag(*logsTimes(27), Held(feathers, 10)), bank = mapOf(feathers to 500)), order(withdrawals = arrayOf(StepItem(feathers, 100))))

        assertEquals(listOf(Take(feathers, 90)), plan.takes)
    }

    @Test
    fun `a stack already in the bag leaves its free slot to the withdrawals after it`() {
        val plan = plan(
            view(bag(*logsTimes(26), Held(feathers, 10)), bank = mapOf(feathers to 500, oak to 50)),
            order(withdrawals = arrayOf(StepItem(feathers), StepItem(oak))),
        )

        assertEquals(listOf(Take(feathers, 500), Take(oak, 1)), plan.takes)
    }

    @Test
    fun `a new stack with the bag full has no room`() {
        val plan = plan(view(bag(*logsTimes(28)), bank = mapOf(coins to 100)), order(withdrawals = arrayOf(StepItem(coins, 100))))

        assertEquals(StuckReason.NoRoom, plan.stuck)
    }

    @Test
    fun `a typed withdrawal with the bag full has no room`() {
        val plan = plan(view(bag(*logsTimes(28)), bank = mapOf(oak to 100)), order(withdrawals = arrayOf(StepItem(oak, 5))))

        assertEquals(StuckReason.NoRoom, plan.stuck)
    }

    @Test
    fun `a withdrawal the bank holds none of is stuck on it`() {
        val plan = plan(view(), order(withdrawals = arrayOf(StepItem(oak))))

        assertEquals(StuckReason.NoneLeft(oak), plan.stuck)
    }

    @Test
    fun `a withdrawal that got nothing at the booth counts as none left`() {
        val plan = plan(view(bank = mapOf(oak to 5)), order(withdrawals = arrayOf(StepItem(oak))), BankTries(emptied = setOf(oak)))

        assertEquals(StuckReason.NoneLeft(oak), plan.stuck)
    }

    @Test
    fun `the first withdrawal that cannot be had says why the step is stuck`() {
        val plan = plan(view(), order(withdrawals = arrayOf(StepItem(oak), StepItem(logs))))

        assertEquals(StuckReason.NoneLeft(oak), plan.stuck)
    }

    @Test
    fun `a step that can move something is not stuck, whatever it cannot have`() {
        val plan = plan(view(bank = mapOf(logs to 5)), order(withdrawals = arrayOf(StepItem(oak), StepItem(logs))))

        assertNull(plan.stuck)
    }

    @Test
    fun `deposits the bank refused with nothing to withdraw mean a full bank`() {
        val plan = plan(view(bag(Held(logs, 1))), order(DepositRule.Everything), BankTries(refused = setOf(logs)))

        assertEquals(StuckReason.BankFull, plan.stuck)
    }

    @Test
    fun `a plan with only withdrawals can move`() {
        assertEquals(true, BankPlan(emptyList(), listOf(Take(oak, 1)), stuck = null).canMove)
    }

    @Test
    fun `an empty plan cannot move`() {
        assertFalse(BankPlan(emptyList(), emptyList(), stuck = null).canMove)
    }

    private val depositing = BankPlan(listOf(0, 1), emptyList(), stuck = null)
    private val withdrawing = BankPlan(emptyList(), listOf(Take(oak, 5)), stuck = null)
    private val idle = BankPlan(emptyList(), emptyList(), stuck = null)
    private val stuck = BankPlan(emptyList(), emptyList(), stuck = StuckReason.NoRoom)

    @Test
    fun `an open bank deposits first`() {
        assertEquals(Deposit(listOf(0, 1)), BankPlanner.decide(view(open = true), depositing.copy(takes = withdrawing.takes), moved = false))
    }

    @Test
    fun `an open bank with nothing to deposit withdraws`() {
        assertEquals(Withdraw(listOf(Take(oak, 5))), BankPlanner.decide(view(open = true), withdrawing, moved = false))
    }

    @Test
    fun `an open bank with nothing left to move closes`() {
        assertEquals(Close, BankPlanner.decide(view(open = true), idle, moved = true))
    }

    @Test
    fun `something to move and no booth blocks`() {
        assertEquals(Blocked(BankBlockedReason.NO_BOOTH), BankPlanner.decide(view().copy(boothFound = false), depositing, moved = false))
    }

    @Test
    fun `something to move next to the booth opens it`() {
        assertEquals(Open, BankPlanner.decide(view().copy(boothUsableFromHere = true), withdrawing, moved = false))
    }

    @Test
    fun `something to move away from the booth walks to it`() {
        assertEquals(WalkToBooth, BankPlanner.decide(view(), depositing, moved = false))
    }

    @Test
    fun `nothing to do is done without a trip, booth or not`() {
        assertEquals(Done, BankPlanner.decide(view().copy(boothFound = false), idle, moved = false))
    }

    @Test
    fun `stuck before anything moved is stuck`() {
        assertEquals(Stuck(StuckReason.NoRoom), BankPlanner.decide(view(), stuck, moved = false))
    }

    @Test
    fun `stuck once something moved is done, taking what was left`() {
        assertEquals(Done, BankPlanner.decide(view(), stuck, moved = true))
    }

    @Test
    fun `a deposit's failures are refused from then on`() {
        assertEquals(BankTries(refused = setOf(logs, oak)), BankTries(refused = setOf(logs)).deposited(BankMove(moved = false, failed = setOf(oak))))
    }

    @Test
    fun `a withdrawal's failures are not asked for again`() {
        assertEquals(BankTries(emptied = setOf(oak)), BankTries().withdrew(BankMove(moved = false, failed = setOf(oak))))
    }

    @Test
    fun `a move that moved something marks the tries as having moved`() {
        assertEquals(listOf(true, true), listOf(BankTries().deposited(BankMove(true, emptySet())).moved, BankTries().withdrew(BankMove(true, emptySet())).moved))
    }

    @Test
    fun `tries that moved stay so whatever the next move does`() {
        val moved = BankTries(moved = true)

        assertEquals(listOf(true, true), listOf(moved.deposited(BankMove(false, setOf(oak))).moved, moved.withdrew(BankMove(false, setOf(oak))).moved))
    }
}
