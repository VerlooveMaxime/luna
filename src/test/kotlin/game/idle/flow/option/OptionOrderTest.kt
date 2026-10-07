package game.idle.flow.option

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OptionOrderTest {

    private fun order(vararg options: StepOption) = OptionOrder.ordered(options.toList()).map { it.label }

    @Test
    fun `greyed rows come after every usable one`() {
        assertEquals(listOf("oak", "willow"), order(row("willow", blocked = "needs Woodcutting 30", level = 30), row("oak", level = 15)))
    }

    @Test
    fun `a lower group comes first`() {
        assertEquals(listOf("Nearest bank", "Al Kharid"), order(row("Al Kharid"), row("Nearest bank", group = -1)))
    }

    @Test
    fun `usable rows the bank holds come before the others`() {
        assertEquals(listOf("oak logs", "logs"), order(row("logs", level = 1), row("oak logs", level = 15, banked = 312)))
    }

    @Test
    fun `usable rows go by level`() {
        assertEquals(listOf("logs", "oak logs"), order(row("oak logs", level = 15), row("logs", level = 1)))
    }

    @Test
    fun `rows of one level go by name, whatever the case`() {
        assertEquals(listOf("copper ore", "Tin ore"), order(row("Tin ore", level = 1), row("copper ore", level = 1)))
    }

    @Test
    fun `greyed rows go by level, the bank's count aside`() {
        val yew = row("yew logs", blocked = "needs Firemaking 60", level = 60, banked = 5)
        val willow = row("willow logs", blocked = "needs Firemaking 30", level = 30)

        assertEquals(listOf("willow logs", "yew logs"), order(yew, willow))
    }
}
