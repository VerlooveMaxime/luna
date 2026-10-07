package game.idle.flow.option

import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ItemOptionsTest {

    private val catalog = ItemCatalog(mapOf(1925 to "Bucket", 3727 to "Bucket", 1511 to "Logs"))

    @Test
    fun `items sharing a name show once, the lowest id`() {
        assertEquals(mapOf(1925 to "Bucket", 1511 to "Logs"), catalog.items(OptionFacts()))
    }

    @Test
    fun `of items sharing a name, the one the bank holds shows`() {
        assertEquals(mapOf(3727 to "Bucket", 1511 to "Logs"), catalog.items(OptionFacts(bank = mapOf(3727 to 2))))
    }

    @Test
    fun `an item row shows the bank's count`() {
        val rows = ItemOptions(catalog).options(OptionContext(OptionFacts(bank = mapOf(1511 to 40))))

        assertEquals(StepOption("1511", "Logs", OptionIcon.Item(1511), "40 in bank", banked = 40), rows.last())
    }

    @Test
    fun `the cache's items leave out notes`() {
        TestWorld.context

        val items = ItemCatalog.fromCache().items(OptionFacts())

        assertEquals(listOf("Willow logs", null), listOf(items[1519], items[1520]))
    }

    @Test
    fun `the cache's items leave out the unnamed`() {
        TestWorld.context

        assertFalse("null" in ItemCatalog.fromCache().items(OptionFacts()).values)
    }
}
