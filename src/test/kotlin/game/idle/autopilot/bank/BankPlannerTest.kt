package game.idle.autopilot.bank

import game.idle.autopilot.bank.BankDecision.Blocked
import game.idle.autopilot.bank.BankDecision.Close
import game.idle.autopilot.bank.BankDecision.Deposit
import game.idle.autopilot.bank.BankDecision.Done
import game.idle.autopilot.bank.BankDecision.Open
import game.idle.autopilot.bank.BankDecision.WalkToBooth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BankPlannerTest {

    private fun view(
        boothFound: Boolean = true,
        usable: Boolean = false,
        open: Boolean = false,
        slots: List<Int> = listOf(0, 1),
    ) = BankView(boothFound, usable, open, slots)

    @Test
    fun `no booth blocks`() {
        assertEquals(Blocked(BankBlockedReason.NO_BOOTH), BankPlanner.decide(view(boothFound = false)))
    }

    @Test
    fun `an open bank with items deposits`() {
        assertEquals(Deposit, BankPlanner.decide(view(open = true)))
    }

    @Test
    fun `an open bank with nothing left closes`() {
        assertEquals(Close, BankPlanner.decide(view(open = true, slots = emptyList())))
    }

    @Test
    fun `nothing to deposit and the bank closed is done`() {
        assertEquals(Done, BankPlanner.decide(view(slots = emptyList())))
    }

    @Test
    fun `next to the booth opens it`() {
        assertEquals(Open, BankPlanner.decide(view(usable = true)))
    }

    @Test
    fun `away from the booth walks to it`() {
        assertEquals(WalkToBooth, BankPlanner.decide(view()))
    }
}
