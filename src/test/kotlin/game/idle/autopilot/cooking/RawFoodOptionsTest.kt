package game.idle.autopilot.cooking

import game.idle.flow.FlowContext
import game.idle.flow.option.FakeNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionFacts
import game.skill.cooking.cookFood.Food
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RawFoodOptionsTest {

    private val shrimps = 317
    private val trout = 335
    private val source = RawFoodOptions(FakeNames(items = mapOf(trout to "Raw trout")))

    @Test
    fun `after a fish step only its catches are offered`() {
        val rows = source.options(OptionContext(before = FlowContext(gathered = setOf(shrimps, 321))))

        assertEquals(setOf("317", "321"), rows.map { it.value }.toSet())
    }

    @Test
    fun `from the bank every raw food is offered`() {
        assertEquals(Food.entries.size, source.options(OptionContext(input = InputSource.BANK)).size)
    }

    @Test
    fun `raw food above the player's Cooking level is greyed`() {
        val context = OptionContext(OptionFacts(levels = mapOf(Skill.COOKING to 14)), input = InputSource.BANK)

        assertEquals("needs Cooking 15", source.options(context).single { it.label == "Raw trout" }.blocked)
    }
}
