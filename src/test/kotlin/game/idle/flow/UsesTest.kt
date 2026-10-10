package game.idle.flow

import game.idle.flow.option.FakeNames
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UsesTest {

    private val names = FakeNames(items = mapOf(440 to "Iron ore", 453 to "Coal", 2349 to "Bronze bar"))

    @Test
    fun `one item is named as the cache names it`() {
        assertEquals("Bronze bar", Uses.text(mapOf(2349 to 1), names))
    }

    @Test
    fun `further items follow in lower case, counts past one after an x`() {
        assertEquals("Iron ore + coal x2", Uses.text(mapOf(440 to 1, 453 to 2), names))
    }

    @Test
    fun `the first item shows its count too`() {
        assertEquals("Bronze bar x3", Uses.text(mapOf(2349 to 3), names))
    }
}
