package game.harness

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MenuOptionsTest {

    @Test
    fun `object options are numbered from one and skip empty slots`() {
        val options = MenuOptions.forObject(listOf("null", "Use-quickly", "null", "null", "null"))

        assertEquals(listOf(OptionView(2, "Use-quickly")), options)
    }

    @Test
    fun `object options the server cannot handle are left out`() {
        val options = MenuOptions.forObject(listOf("Open", "null", "null", "Guide", "Exchange"))

        assertEquals(listOf(OptionView(1, "Open")), options)
    }

    @Test
    fun `object hidden actions are left out as the client does`() {
        val options = MenuOptions.forObject(listOf("Chop down", "null", "Hidden", "null", "null"))

        assertEquals(listOf(OptionView(1, "Chop down")), options)
    }

    @Test
    fun `blank actions are left out`() {
        val options = MenuOptions.forObject(listOf(" ", "Open"))

        assertEquals(listOf(OptionView(2, "Open")), options)
    }

    @Test
    fun `npc options cover all five slots`() {
        val options = MenuOptions.forNpc(listOf("Talk-to", "Attack", "Pickpocket", "Trade", "Follow"))

        assertEquals((1..5).toList(), options.map { it.option })
    }

    @Test
    fun `npc hidden actions are left out`() {
        val options = MenuOptions.forNpc(listOf("Talk-to", "hidden", "Bank", "null", "null"))

        assertEquals(listOf(OptionView(1, "Talk-to"), OptionView(3, "Bank")), options)
    }

    @Test
    fun `inventory option 5 falls back to Drop`() {
        val options = MenuOptions.forInventoryItem(listOf("null", "Wield", "null", "null", "null"))

        assertEquals(listOf(OptionView(2, "Wield"), OptionView(5, "Drop")), options)
    }

    @Test
    fun `inventory option 5 keeps a defined action`() {
        val options = MenuOptions.forInventoryItem(listOf("null", "null", "null", "null", "Destroy"))

        assertEquals(listOf(OptionView(5, "Destroy")), options)
    }

    @Test
    fun `inventory actions named hidden are kept because the client keeps them`() {
        val options = MenuOptions.forInventoryItem(listOf("hidden", "null", "null", "null", "null"))

        assertEquals(listOf(OptionView(1, "hidden"), OptionView(5, "Drop")), options)
    }

    @Test
    fun `a short inventory action list still gets Drop`() {
        val options = MenuOptions.forInventoryItem(listOf("Eat"))

        assertEquals(listOf(OptionView(1, "Eat"), OptionView(5, "Drop")), options)
    }

    @Test
    fun `ground option 3 falls back to Take`() {
        val options = MenuOptions.forGroundItem(listOf("null", "null", "null", "Light", "null"))

        assertEquals(listOf(OptionView(3, "Take"), OptionView(4, "Light")), options)
    }

    @Test
    fun `a hidden ground option 3 still shows Take`() {
        val options = MenuOptions.forGroundItem(listOf("null", "null", "hidden", "null", "null"))

        assertEquals(listOf(OptionView(3, "Take")), options)
    }

    @Test
    fun `ground options the server cannot handle are left out`() {
        val options = MenuOptions.forGroundItem(listOf("Open", "Read", "Take", "null", "Examine"))

        assertEquals(listOf(OptionView(3, "Take")), options)
    }
}
