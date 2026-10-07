package game.idle.autopilot.smelting

import game.idle.flow.FlowContext
import game.idle.flow.option.InputSource
import game.idle.flow.option.LunaGameNames
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionFacts
import game.skill.smithing.BarType
import game.testworld.TestWorld
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Luna's bars name their ores from the cache, so these run on [TestWorld]. */
class BarOptionsTest {

    private val copper = 436
    private val tin = 438
    private val iron = 440
    private val coal = 453

    @BeforeEach
    fun cache() {
        TestWorld.context
    }

    private fun rows(context: OptionContext) = BarOptions(LunaGameNames).options(context)

    @Test
    fun `after mining copper and tin only bronze is offered`() {
        val rows = rows(OptionContext(before = FlowContext(gathered = setOf(copper, tin))))

        assertEquals(listOf("bronze"), rows.map { it.value })
    }

    @Test
    fun `a bar needs every ore from earlier steps`() {
        val rows = rows(OptionContext(before = FlowContext(gathered = setOf(iron))))

        assertEquals(listOf("iron"), rows.map { it.value })
    }

    @Test
    fun `from the bank every bar is offered`() {
        assertEquals(BarType.entries.size, rows(OptionContext(input = InputSource.BANK)).size)
    }

    @Test
    fun `steel is greyed below Smithing 30, named as the cache names the bar`() {
        val context = OptionContext(OptionFacts(levels = mapOf(Skill.SMITHING to 29)), before = FlowContext(gathered = setOf(iron, coal)))

        val steel = rows(context).single { it.value == "steel" }

        assertEquals(listOf("Steel bar", "needs Smithing 30"), listOf(steel.label, steel.blocked))
    }
}
