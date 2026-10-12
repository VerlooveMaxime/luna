package game.idle.autopilot.bank

import game.idle.flow.Meter
import game.idle.flow.Reason
import game.idle.flow.StepItem
import game.idle.flow.StepOutcome
import game.idle.flow.StepOutcome.Approached
import game.idle.flow.StepOutcome.Finished
import game.idle.flow.StepOutcome.Worked
import game.idle.flow.Target
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BankWorkTest {

    private val logs = 1511
    private val oak = 1521

    private val bagOfLogs = listOf<Held?>(Held(logs, 1), Held(logs, 1)) + List(26) { null }
    private val banker = FakeBanker(BankView(boothFound = true, boothUsableFromHere = true, bankOpen = false, bagOfLogs, emptyMap()))

    private fun work(order: BankOrder = EVERYTHING) = BankWork(banker, order)

    private fun withdrawing(ifStuck: IfStuck) = BankOrder(DepositRule.Only(emptySet()), listOf(StepItem(oak)), ifStuck)

    @Test
    fun `a bank step counts nothing`() {
        assertEquals(Meter.None, work().meter)
    }

    @Test
    fun `banking has nothing to settle before the step may end`() {
        assertFalse(work().finishing())
    }

    @Test
    fun `busy follows the banker`() {
        banker.busy = true

        assertTrue(work().isBusy())
    }

    @Test
    fun `interrupting breaks off the walk to the booth`() {
        work().interrupt()

        assertEquals(listOf("interrupt"), banker.steps)
    }

    @Test
    fun `a trip walks to the booth when it is out of reach, which approaches it`() {
        banker.view = banker.view.copy(boothUsableFromHere = false)

        val outcome = work().act(left = null)

        assertEquals(Approached to listOf("walk"), outcome to banker.steps)
    }

    @Test
    fun `a full trip opens, deposits, closes and finishes, the deposit its work`() {
        val work = work()

        val outcomes = List(4) { work.act(left = null) }

        assertEquals(listOf(Approached, Worked, Approached, Finished), outcomes)
    }

    @Test
    fun `a full trip clicks open, deposit and close`() {
        val work = work()

        repeat(4) { work.act(left = null) }

        assertEquals(listOf("open", "deposit [0, 1]", "close"), banker.steps)
    }

    @Test
    fun `a trip withdraws after depositing, which is work`() {
        banker.view = banker.view.copy(bank = mapOf(oak to 50))
        val work = work(BankOrder(DepositRule.Everything, listOf(StepItem(oak, 5)), IfStuck.SKIP))
        work.act(left = null)
        work.act(left = null)

        val outcome = work.act(left = null)

        assertEquals(Worked to listOf("open", "deposit [0, 1]", "withdraw [${Take(oak, 5)}]"), outcome to banker.steps)
    }

    @Test
    fun `an item withdrawn is not deposited again, so the trip closes`() {
        banker.view = banker.view.copy(bag = List(28) { null }, bank = mapOf(oak to 1))
        val work = work(BankOrder(DepositRule.Everything, listOf(StepItem(oak, 1)), IfStuck.SKIP))
        repeat(3) { work.act(left = null) }

        val outcome = work.act(left = null)

        assertEquals(Finished to listOf("open", "withdraw [${Take(oak, 1)}]", "close"), outcome to banker.steps)
    }

    @Test
    fun `a refused deposit is no work`() {
        banker.bankFull = true
        val work = work()
        work.act(left = null)

        assertEquals(Approached, work.act(left = null))
    }

    @Test
    fun `a withdrawal that got nothing is no work`() {
        banker.view = banker.view.copy(bag = List(28) { null }, bank = mapOf(oak to 50))
        banker.handsOut = false
        val work = work(withdrawing(IfStuck.STOP))
        work.act(left = null)

        assertEquals(Approached, work.act(left = null))
    }

    @Test
    fun `a withdrawal that got nothing is not tried again`() {
        banker.view = banker.view.copy(bag = List(28) { null }, bank = mapOf(oak to 50))
        banker.handsOut = false
        val work = work(withdrawing(IfStuck.STOP))

        repeat(4) { work.act(left = null) }

        assertEquals(listOf("open", "withdraw [${Take(oak, 28)}]", "close"), banker.steps)
    }

    @Test
    fun `a step that moved nothing at the booth is stuck once the bank is closed`() {
        banker.view = banker.view.copy(bag = List(28) { null }, bank = mapOf(oak to 50))
        banker.handsOut = false
        val work = work(withdrawing(IfStuck.STOP))
        repeat(3) { work.act(left = null) }

        assertEquals(StepOutcome.Stuck(Reason.NoneLeftInBank(oak), stopsFlow = true), work.act(left = null))
    }

    @Test
    fun `a deposit the bank refused is not tried again`() {
        banker.bankFull = true
        val work = work()

        repeat(4) { work.act(left = null) }

        assertEquals(listOf("open", "deposit [0, 1]", "close"), banker.steps)
    }

    @Test
    fun `a full bank that took nothing is stuck`() {
        banker.bankFull = true
        val work = work(EVERYTHING.copy(ifStuck = IfStuck.STOP))
        repeat(3) { work.act(left = null) }

        assertEquals(StepOutcome.Stuck(Reason.BankFull, stopsFlow = true), work.act(left = null))
    }

    @Test
    fun `a step that moved something finishes when nothing more can move`() {
        banker.view = banker.view.copy(bank = mapOf(oak to 50))
        banker.handsOut = false
        val work = work(BankOrder(DepositRule.Everything, listOf(StepItem(oak)), IfStuck.STOP))
        repeat(4) { work.act(left = null) }

        assertEquals(Finished, work.act(left = null))
    }

    @Test
    fun `nothing to do finishes the step at once, without a trip`() {
        val outcome = work(withdrawing(IfStuck.SKIP).copy(withdrawals = emptyList())).act(left = null)

        assertEquals(Finished to emptyList<String>(), outcome to banker.steps)
    }

    @Test
    fun `stuck before walking with Skip skips the step's turn, without a trip`() {
        val outcome = work(withdrawing(IfStuck.SKIP)).act(left = null)

        assertEquals(StepOutcome.Stuck(Reason.NoneLeftInBank(oak), stopsFlow = false) to emptyList<String>(), outcome to banker.steps)
    }

    @Test
    fun `stuck with Stop the flow stops it`() {
        assertEquals(StepOutcome.Stuck(Reason.NoneLeftInBank(oak), stopsFlow = true), work(withdrawing(IfStuck.STOP)).act(left = null))
    }

    @Test
    fun `stuck for room says the bag is full`() {
        banker.view = banker.view.copy(bag = List(28) { Held(logs, 1) }, bank = mapOf(oak to 5))
        val work = work(BankOrder(DepositRule.Only(emptySet()), listOf(StepItem(oak, 3)), IfStuck.STOP))

        assertEquals(StepOutcome.Stuck(Reason.BagFull(emptyList()), stopsFlow = true), work.act(left = null))
    }

    @Test
    fun `something to move and no booth on this floor blocks, the player must get to one`() {
        banker.view = banker.view.copy(boothFound = false)

        assertEquals(StepOutcome.Blocked(Reason.NoneInReach(Target.BANK_BOOTH)), work().act(left = null))
    }
}
