package game.idle.autopilot.making

import game.idle.flow.FlowContext
import game.idle.flow.option.InputSource
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionFacts
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.ProcessOptions
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MakeOptionsTest {

    private val shafts = Recipe(52, "Arrow shaft", Skill.FLETCHING, 1, listOf(RecipeWay(946, 1511, mapOf(1511 to 1), setOf(946))))
    private val longbow = Recipe(48, "Longbow (u)", Skill.FLETCHING, 10, listOf(RecipeWay(946, 1511, mapOf(1511 to 1), setOf(946))))
    private val dough = BREAD_DOUGH.copy(
        ways = listOf(RecipeWay(1933, 1929, mapOf(1933 to 1, 1929 to 1)), RecipeWay(1933, 1937, mapOf(1933 to 1, 1937 to 1))),
    )
    private val source = MakeOptions(RecipeCatalog(listOf(shafts, longbow, dough)))

    private fun afterChopping(level: Int = 1) =
        OptionContext(OptionFacts(levels = mapOf(Skill.FLETCHING to level)), before = FlowContext(gathered = setOf(1511)))

    private fun fromBank(bank: Map<Int, Int>) = OptionContext(OptionFacts(bank = bank), input = InputSource.BANK)

    @Test
    fun `after a chop step the logs' products are offered, the knife being a tool`() {
        assertEquals(listOf("arrow shaft", "longbow (u)"), source.options(afterChopping()).map { it.value })
    }

    @Test
    fun `a row shows the product under its label and notes where its input comes from`() {
        val row = source.options(afterChopping()).first()

        assertEquals(listOf("Arrow shaft", OptionIcon.Item(52), ProcessOptions.FROM_EARLIER), listOf(row.label, row.icon, row.note))
    }

    @Test
    fun `a product above the player's level is greyed`() {
        assertEquals("needs Fletching 10", source.options(afterChopping(level = 9)).single { it.value == "longbow (u)" }.blocked)
    }

    @Test
    fun `from the bank every product is offered`() {
        assertEquals(listOf("arrow shaft", "longbow (u)", "bread dough"), source.options(fromBank(emptyMap())).map { it.value })
    }

    @Test
    fun `from the bank a product counts the way the bank holds the most of`() {
        val row = source.options(fromBank(mapOf(1933 to 40, 1929 to 2, 1937 to 25))).single { it.value == "bread dough" }

        assertEquals(25, row.banked)
    }
}
