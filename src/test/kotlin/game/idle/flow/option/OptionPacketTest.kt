package game.idle.flow.option

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OptionPacketTest {

    private val packet = OptionPacket()

    @Test
    fun `a row counts its label and note as ended strings, an icon and a flags byte`() {
        assertEquals(4 + 12 + 3 + 1, packet.size(row("Oak", note = "312 in bank")))
    }

    @Test
    fun `a greyed row counts its reason instead of its note`() {
        assertEquals(4 + 21 + 3 + 1, packet.size(row("Yew", blocked = "needs Woodcutting 60", note = "Woodcutting 60 extra")))
    }

    @Test
    fun `rows within the budget fit one packet`() {
        assertTrue(OptionPacket(budget = 18).fits(listOf(row("Oak"), row("Yew"))))
    }

    @Test
    fun `rows over the budget do not fit`() {
        assertFalse(OptionPacket(budget = 17).fits(listOf(row("Oak"), row("Yew"))))
    }

    @Test
    fun `the default budget leaves room in the client's 5000-byte buffer`() {
        assertEquals(4900, OptionPacket.BUDGET)
    }

    @Test
    fun `a query keeps the rows whose label holds every typed word, whatever the case`() {
        val rows = listOf(row("Bronze dagger"), row("Iron dagger"), row("Bronze axe"))

        assertEquals(listOf("Bronze dagger"), packet.matches(rows, " DAGGER  bron").options.map { it.label })
    }

    @Test
    fun `a query answers in the search order`() {
        val rows = listOf(row("Iron dagger", level = 15), row("Bronze dagger", level = 1))

        assertEquals(listOf("Bronze dagger", "Iron dagger"), packet.matches(rows, "dagger").options.map { it.label })
    }

    @Test
    fun `a query cuts what does not fit one packet and says so`() {
        val matches = OptionPacket(budget = 18).matches(listOf(row("Oak"), row("Yew"), row("Ash")), "")

        assertEquals(OptionMatches(listOf(row("Ash"), row("Oak")), cut = true), matches)
    }

    @Test
    fun `a query that fits says nothing was cut`() {
        assertFalse(packet.matches(listOf(row("Oak")), "oak").cut)
    }
}
