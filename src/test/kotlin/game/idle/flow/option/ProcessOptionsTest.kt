package game.idle.flow.option

import game.idle.flow.FlowContext
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProcessOptionsTest {

    private val copper = 436
    private val tin = 438
    private val bronze = 2349
    private val fromBank = OptionContext(OptionFacts(bank = mapOf(copper to 120, tin to 96)), input = InputSource.BANK)

    private fun bar(context: OptionContext, level: Int = 1) =
        ProcessOptions.option(context, bronze, "Bronze bar", listOf(copper, tin), Skill.SMITHING, level)

    @Test
    fun `from earlier steps a row needs every input gathered before it`() {
        assertNull(bar(OptionContext(before = FlowContext(gathered = setOf(copper)))))
    }

    @Test
    fun `from earlier steps a row says where its input comes from`() {
        val row = bar(OptionContext(before = FlowContext(gathered = setOf(copper, tin, 999))))

        assertEquals(StepOption("2349", "Bronze bar", OptionIcon.Item(bronze), "from an earlier step", level = 1), row)
    }

    @Test
    fun `from the bank every row is offered, with the bank's count of its first input`() {
        assertEquals("120 in bank ...", bar(fromBank)?.note)
    }

    @Test
    fun `a row with one input from the bank shows its count alone`() {
        val row = ProcessOptions.option(fromBank, 1511, "Logs", listOf(copper), Skill.FIREMAKING, level = 1)

        assertEquals("120 in bank", row?.note)
    }

    @Test
    fun `from the bank a row orders by its scarcest input`() {
        assertEquals(96, bar(fromBank)?.banked)
    }

    @Test
    fun `a level not reached greys the row`() {
        assertEquals("needs Smithing 30", bar(fromBank, level = 30)?.blocked)
    }

    @Test
    fun `a row may keep another value than the product's id`() {
        val row = ProcessOptions.option(fromBank, bronze, "Bronze bar", listOf(copper), Skill.SMITHING, 1, value = "bronze")

        assertEquals("bronze", row?.value)
    }
}
