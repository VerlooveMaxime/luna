package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StepItemsTest {

    private fun settings(text: String) = StepSettings("bank", mapOf("withdraw" to text))

    @Test
    fun `a list reads its ids in order, an amount after a colon`() {
        assertEquals(listOf(StepItem(1511, 14), StepItem(590)), StepItems.read(settings("1511:14,590"), "withdraw"))
    }

    @Test
    fun `a setting the step does not have reads as no items`() {
        assertEquals(listOf<StepItem>(), StepItems.read(StepSettings("bank"), "withdraw"))
    }

    @Test
    fun `text naming no item is skipped`() {
        assertEquals(listOf(StepItem(590)), StepItems.read(settings("logs,590,"), "withdraw"))
    }

    @Test
    fun `an amount that is no number reads as all`() {
        assertEquals(listOf(StepItem(1511)), StepItems.read(settings("1511:many"), "withdraw"))
    }

    @Test
    fun `an item listed twice counts once`() {
        assertEquals(listOf(StepItem(1511, 5)), StepItems.read(settings("1511:5,1511:9"), "withdraw"))
    }

    @Test
    fun `the ids are the items without their amounts`() {
        assertEquals(listOf(1511, 590), StepItems.ids(settings("1511:14,590"), "withdraw"))
    }

    @Test
    fun `items are written as they are read`() {
        assertEquals("1511:14,590", StepItems.text(listOf(StepItem(1511, 14), StepItem(590))))
    }

    @Test
    fun `toggling an item the list lacks adds it at the end`() {
        assertEquals("1511:14,590,1521", StepItems.toggled(settings("1511:14,590"), "withdraw", 1521)["withdraw"])
    }

    @Test
    fun `toggling an item the list has takes it out`() {
        assertEquals("590", StepItems.toggled(settings("1511:14,590"), "withdraw", 1511)["withdraw"])
    }

    @Test
    fun `toggling the last item out leaves no setting`() {
        assertEquals(StepSettings("bank"), StepItems.toggled(settings("590"), "withdraw", 590))
    }

    @Test
    fun `an amount below 1 reads as all`() {
        assertEquals(listOf(StepItem(1511), StepItem(590)), StepItems.read(settings("1511:0,590:-3"), "withdraw"))
    }

    @Test
    fun `an item's amount can be set, the others kept`() {
        assertEquals("1511:20,590:1", StepItems.withAmount(settings("1511,590:1"), "withdraw", 1511, 20)["withdraw"])
    }

    @Test
    fun `an item's amount can be put back to all`() {
        assertEquals("1511,590:1", StepItems.withAmount(settings("1511:14,590:1"), "withdraw", 1511, amount = null)["withdraw"])
    }

    @Test
    fun `setting the amount of an item the list lacks leaves the list as it is`() {
        assertEquals("590:1", StepItems.withAmount(settings("590:1"), "withdraw", 1511, 5)["withdraw"])
    }
}
