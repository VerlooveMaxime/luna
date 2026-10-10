package game.idle.flow.option

import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ItemOptionsTest {

    private val catalog = ItemCatalog(mapOf(1925 to "Bucket", 3727 to "Bucket", 1511 to "Logs"), notes = mapOf(1926 to 1925, 1512 to 1511))

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
    fun `a note stands for its item`() {
        assertEquals(1511, catalog.unnoted(1512))
    }

    @Test
    fun `an item that is no note stands for itself`() {
        assertEquals(1511, catalog.unnoted(1511))
    }

    @Test
    fun `alike items are those of the same name and their notes`() {
        assertEquals(setOf(1925, 3727, 1926), catalog.alike(3727))
    }

    @Test
    fun `a note is alike its item's name`() {
        assertEquals(setOf(1511, 1512), catalog.alike(1512))
    }

    @Test
    fun `an id that names no item has nothing alike`() {
        assertEquals(emptySet<Int>(), catalog.alike(99999))
    }

    @Test
    fun `the cache's notes stand for their items`() {
        TestWorld.context

        assertEquals(1519, ItemCatalog.fromCache().unnoted(1520))
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
