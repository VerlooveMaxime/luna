package game.idle.autopilot.smithing

import game.idle.flow.FlowContext
import game.idle.flow.option.InputSource
import game.idle.flow.option.LunaGameNames
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionFacts
import game.skill.smithing.smithBar.SmithingTable
import game.testworld.TestWorld
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Luna's smithing tables name their items from the cache, so these run on [TestWorld]. */
class SmithItemOptionsTest {

    private val bronzeBar = 2349

    @BeforeEach
    fun cache() {
        TestWorld.context
    }

    private fun rows(context: OptionContext) = SmithItemOptions(LunaGameNames).options(context)

    @Test
    fun `after smelting bronze only bronze items are offered`() {
        val labels = rows(OptionContext(before = FlowContext(gathered = setOf(bronzeBar)))).map { it.label }

        assertTrue(labels.all { it.startsWith("Bronze") })
    }

    @Test
    fun `from the bank every item of every metal is offered`() {
        assertEquals(SmithingTable.entries.sumOf { it.items.size }, rows(OptionContext(input = InputSource.BANK)).size)
    }

    @Test
    fun `an item is kept under its id and greyed below its Smithing level`() {
        val context = OptionContext(OptionFacts(levels = mapOf(Skill.SMITHING to 1)), before = FlowContext(gathered = setOf(bronzeBar)))

        val mace = rows(context).single { it.label == "Bronze mace" }

        assertEquals(listOf("1422", "needs Smithing 2"), listOf(mace.value, mace.blocked))
    }
}
