package game.idle.ui

import game.idle.flow.option.OptionIcon
import game.idle.flow.option.StepOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchRowTest {

    @Test
    fun `a usable option keeps its label, note and icon`() {
        val option = StepOption("oak", "Oak", OptionIcon.Item(1521), note = "Woodcutting 15")

        assertEquals(SearchRow(3, "Oak", "Woodcutting 15", false, WidgetPicture.Item(1521)), SearchRow.of(3, option))
    }

    @Test
    fun `a greyed option shows its reason instead of its note`() {
        val option = StepOption("yew", "Yew", OptionIcon.Item(1515), note = "Woodcutting 60", blocked = "needs Woodcutting 60")

        assertEquals(SearchRow(4, "Yew", "needs Woodcutting 60", true, WidgetPicture.Item(1515)), SearchRow.of(4, option))
    }

    @Test
    fun `a row describes itself by place, label, note and picture`() {
        assertEquals("3 Oak / Woodcutting 15 / item 1521", SearchRow(3, "Oak", "Woodcutting 15", false, WidgetPicture.Item(1521)).describe())
    }

    @Test
    fun `a greyed row says so`() {
        assertEquals("4 Yew / needs Woodcutting 60 / item 1515 / greyed",
            SearchRow(4, "Yew", "needs Woodcutting 60", true, WidgetPicture.Item(1515)).describe())
    }

    @Test
    fun `a row without a note leaves it out`() {
        assertEquals("0 Lumbridge / media mapfunction 5", SearchRow(0, "Lumbridge", "", false, WidgetPicture.Media("mapfunction", 5)).describe())
    }
}
