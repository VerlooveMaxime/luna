package game.idle.autopilot.bank

import game.idle.flow.Reason
import game.idle.flow.Said
import game.idle.flow.StepItem
import game.idle.flow.StepStop
import game.idle.flow.Target
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BankActivityTest {

    private val logs = 1511
    private val oak = 1521

    /** A banker over a bag and a bank it moves items between, as long as [bankFull] is false. */
    private class FakeBanker(var view: BankView) : Banker {
        var busy = false
        var bankFull = false
        val steps = mutableListOf<String>()

        override fun isBusy() = busy
        override fun look() = view
        override fun stacks(id: Int) = false
        override fun walkToBooth() { steps += "walk" }
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
            return BankMove(moved = false, failed = takes.map { it.id }.toSet())
        }

        override fun close() {
            steps += "close"
            view = view.copy(bankOpen = false)
        }
    }

    private val bagOfLogs = listOf<Held?>(Held(logs, 1), Held(logs, 1)) + List(26) { null }
    private val banker = FakeBanker(BankView(boothFound = true, boothUsableFromHere = true, bankOpen = false, bagOfLogs, emptyMap()))

    private fun activity(order: BankOrder = EVERYTHING) = BankActivity(banker, order)

    private fun withdrawing(ifStuck: IfStuck) = BankOrder(DepositRule.Only(emptySet()), listOf(StepItem(oak)), ifStuck)

    @Test
    fun `busy follows the banker`() {
        banker.busy = true

        assertTrue(activity().isBusy())
    }

    @Test
    fun `a trip walks to the booth when it is out of reach`() {
        banker.view = banker.view.copy(boothUsableFromHere = false)

        activity().act()

        assertEquals(listOf("walk"), banker.steps)
    }

    @Test
    fun `a full trip opens, deposits, closes and is done`() {
        val activity = activity()

        repeat(4) { activity.act() }

        assertEquals(listOf("open", "deposit [0, 1]", "close"), banker.steps)
        assertTrue(activity.isDone())
    }

    @Test
    fun `a trip withdraws after depositing`() {
        banker.view = banker.view.copy(bank = mapOf(oak to 50))
        val activity = activity(BankOrder(DepositRule.Everything, listOf(StepItem(oak, 5)), IfStuck.SKIP))

        repeat(3) { activity.act() }

        assertEquals(listOf("open", "deposit [0, 1]", "withdraw [${Take(oak, 5)}]"), banker.steps)
    }

    @Test
    fun `a withdrawal that got nothing is not tried again`() {
        banker.view = banker.view.copy(bag = List(28) { null }, bank = mapOf(oak to 50))
        val activity = activity(withdrawing(IfStuck.STOP))

        repeat(4) { activity.act() }

        assertEquals(listOf("open", "withdraw [${Take(oak, 28)}]", "close"), banker.steps)
    }

    @Test
    fun `a step that moved nothing at the booth is stuck once the bank is closed`() {
        banker.view = banker.view.copy(bag = List(28) { null }, bank = mapOf(oak to 50))
        val activity = activity(withdrawing(IfStuck.STOP))

        repeat(4) { activity.act() }

        assertEquals(StepStop.Stuck(Reason.NoneLeftInBank(oak)), activity.stopping())
    }

    @Test
    fun `a deposit the bank refused is not tried again`() {
        banker.bankFull = true
        val activity = activity()

        repeat(4) { activity.act() }

        assertEquals(listOf("open", "deposit [0, 1]", "close"), banker.steps)
    }

    @Test
    fun `a full bank that took nothing is stuck`() {
        banker.bankFull = true
        val activity = activity(EVERYTHING.copy(ifStuck = IfStuck.STOP))

        repeat(4) { activity.act() }

        assertEquals(StepStop.Stuck(Reason.BankFull), activity.stopping())
    }

    @Test
    fun `a step that moved something ends when nothing more can move`() {
        banker.view = banker.view.copy(bank = mapOf(oak to 50))
        val activity = activity(BankOrder(DepositRule.Everything, listOf(StepItem(oak)), IfStuck.STOP))

        repeat(5) { activity.act() }

        assertEquals(listOf(true, null), listOf(activity.isDone(), activity.stopping()))
    }

    @Test
    fun `nothing to do ends the step at once, without a trip`() {
        val activity = activity(withdrawing(IfStuck.SKIP).copy(withdrawals = emptyList()))

        activity.act()

        assertEquals(listOf(true, emptyList<String>()), listOf(activity.isDone(), banker.steps))
    }

    @Test
    fun `stuck before walking with Skip says so and ends the step`() {
        val activity = activity(withdrawing(IfStuck.SKIP))

        activity.act()

        assertEquals(
            listOf(true, Said.Skipped(Reason.NoneLeftInBank(oak)), emptyList<String>()),
            listOf(activity.isDone(), activity.said(), banker.steps),
        )
    }

    @Test
    fun `what the step said is given once`() {
        val activity = activity(withdrawing(IfStuck.SKIP))
        activity.act()
        activity.said()

        assertNull(activity.said())
    }

    @Test
    fun `stuck with Stop the flow stops it instead of ending the step, saying nothing`() {
        val activity = activity(withdrawing(IfStuck.STOP))

        activity.act()

        assertEquals(listOf(false, null), listOf(activity.isDone(), activity.said()))
    }

    @Test
    fun `a step that is not stuck says nothing`() {
        val activity = activity()

        activity.act()

        assertNull(activity.said())
    }

    @Test
    fun `stuck for room says the bag is full`() {
        banker.view = banker.view.copy(bag = List(28) { Held(logs, 1) }, bank = mapOf(oak to 5))
        val activity = activity(BankOrder(DepositRule.Only(emptySet()), listOf(StepItem(oak, 3)), IfStuck.STOP))

        activity.act()

        assertEquals(StepStop.Stuck(Reason.BagFull(emptyList())), activity.stopping())
    }

    @Test
    fun `a step that is not stuck stops nothing`() {
        assertNull(activity().stopping())
    }

    @Test
    fun `a block is reported with its reason`() {
        banker.view = banker.view.copy(boothFound = false)
        val activity = activity()

        activity.act()

        assertEquals(Reason.NoneInReach(Target.BANK_BOOTH), activity.blocked())
    }

    @Test
    fun `a block clears on the next decision that is not one`() {
        banker.view = banker.view.copy(boothFound = false)
        val activity = activity()
        activity.act()
        banker.view = banker.view.copy(boothFound = true)

        activity.act()

        assertNull(activity.blocked())
    }

    @Test
    fun `a blocked step is not done`() {
        banker.view = banker.view.copy(boothFound = false)
        val activity = activity()

        activity.act()

        assertFalse(activity.isDone())
    }
}
