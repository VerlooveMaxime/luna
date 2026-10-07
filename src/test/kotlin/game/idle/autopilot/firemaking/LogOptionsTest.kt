package game.idle.autopilot.firemaking

import game.idle.flow.FlowContext
import game.idle.flow.option.FakeNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionFacts
import game.skill.firemaking.Log
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LogOptionsTest {

    private val source = LogOptions(FakeNames(items = mapOf(1519 to "Willow logs")))

    @Test
    fun `after a chop step only the logs being cut are offered`() {
        val rows = source.options(OptionContext(before = FlowContext(gathered = setOf(1519, 438))))

        assertEquals(listOf("1519"), rows.map { it.value })
    }

    @Test
    fun `from the bank every log is offered`() {
        val rows = source.options(OptionContext(input = InputSource.BANK))

        assertEquals(Log.entries.map { it.id.toString() }, rows.map { it.value })
    }

    @Test
    fun `logs above the player's Firemaking level are greyed`() {
        val context = OptionContext(OptionFacts(levels = mapOf(Skill.FIREMAKING to 29)), input = InputSource.BANK)

        assertEquals("needs Firemaking 30", source.options(context).single { it.label == "Willow logs" }.blocked)
    }
}
