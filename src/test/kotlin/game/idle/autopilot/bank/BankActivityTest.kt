package game.idle.autopilot.bank

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BankActivityTest {

    private class FakeBanker(var view: BankView) : Banker {
        var busy = false
        val steps = mutableListOf<String>()
        override fun isBusy() = busy
        override fun look() = view
        override fun walkToBooth() { steps += "walk" }
        override fun open() { steps += "open" }
        override fun deposit(slots: List<Int>) { steps += "deposit $slots" }
        override fun close() { steps += "close" }
    }

    private val banker = FakeBanker(BankView(boothFound = true, boothUsableFromHere = false, bankOpen = false, depositableSlots = listOf(3, 5)))
    private val activity = BankActivity(banker)

    @Test
    fun `busy follows the banker`() {
        banker.busy = true

        assertTrue(activity.isBusy())
    }

    @Test
    fun `a full trip walks, opens, deposits, closes and is done`() {
        activity.act()
        banker.view = banker.view.copy(boothUsableFromHere = true)
        activity.act()
        banker.view = banker.view.copy(bankOpen = true)
        activity.act()
        banker.view = banker.view.copy(depositableSlots = emptyList())
        activity.act()
        banker.view = banker.view.copy(bankOpen = false)
        assertFalse(activity.isDone())

        activity.act()

        assertEquals(listOf("walk", "open", "deposit [3, 5]", "close"), banker.steps)
        assertTrue(activity.isDone())
    }

    @Test
    fun `a block is reported with its reason`() {
        banker.view = banker.view.copy(boothFound = false)

        activity.act()

        assertEquals(BankBlockedReason.NO_BOOTH.message, activity.blocked())
    }

    @Test
    fun `a block clears on the next decision that is not one`() {
        banker.view = banker.view.copy(boothFound = false)
        activity.act()
        banker.view = banker.view.copy(boothFound = true)

        activity.act()

        assertNull(activity.blocked())
    }

    @Test
    fun `a missing booth blocks the step`() {
        banker.view = banker.view.copy(boothFound = false)

        activity.act()
        activity.act()

        assertEquals(BankBlockedReason.NO_BOOTH.message, activity.blocked())
    }
}
