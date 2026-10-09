package game.idle.flow.option

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OptionPacketTest {

    /** Every icon 3 bytes, as an item's is. */
    private val packet = OptionPacket({ 3 })

    @Test
    fun `a row counts its index, a flags byte, its label and note as ended strings and its icon`() {
        assertEquals(2 + 1 + 4 + 12 + 3, packet.size(row("Oak", note = "312 in bank")))
    }

    @Test
    fun `a greyed row counts its reason instead of its note`() {
        assertEquals(2 + 1 + 4 + 21 + 3, packet.size(row("Yew", blocked = "needs Woodcutting 60", note = "Woodcutting 60 extra")))
    }

    @Test
    fun `an icon counts what its encoding takes`() {
        assertEquals(2 + 1 + 4 + 1 + 14, OptionPacket({ 14 }).size(row("Oak")))
    }

    @Test
    fun `rows within the budget all fit`() {
        assertEquals(listOf(row("Oak"), row("Yew")), OptionPacket({ 3 }, budget = 22).fit(listOf(row("Oak"), row("Yew"))))
    }

    @Test
    fun `rows past the budget are left for the next page`() {
        assertEquals(listOf(row("Oak")), OptionPacket({ 3 }, budget = 21).fit(listOf(row("Oak"), row("Yew"))))
    }

    @Test
    fun `the default budget leaves room in the client's 5000-byte buffer`() {
        assertEquals(4900, OptionPacket.BUDGET)
    }

    @Test
    fun `a search matches when every typed word is in the label, whatever the case`() {
        assertEquals(listOf(true, false), listOf(row("Bronze dagger"), row("Bronze axe")).map { OptionSearch.matches(it, " DAGGER  bron") })
    }

    @Test
    fun `an empty search matches everything`() {
        assertEquals(true, OptionSearch.matches(row("Oak"), ""))
    }

    @Test
    fun `typed words are lower case and spaces between them do not count`() {
        assertEquals(listOf("oak", "logs"), OptionSearch.words("  Oak   LOGS "))
    }
}
