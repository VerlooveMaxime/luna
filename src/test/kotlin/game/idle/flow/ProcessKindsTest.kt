package game.idle.flow

import game.idle.flow.option.FakeNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.OptionSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProcessKindsTest {

    private val logs = 1511
    private val oakLogs = 1521
    private val rawShrimps = 317
    private val names = FakeNames(items = mapOf(logs to "Logs", oakLogs to "Oak logs"))
    private val kinds = ProcessKinds("logs", kind = "light", what = "logs", usable = setOf(logs, oakLogs), names)

    private fun light(list: String? = null) = StepSettings("light", list?.let { mapOf("logs" to it) }.orEmpty())

    @Test
    fun `items the step cannot use are left out`() {
        assertEquals(listOf(oakLogs), kinds.ids(light("$rawShrimps,$oakLogs")))
    }

    @Test
    fun `a new step on earlier steps starts with every kind they get, in flow order`() {
        val before = FlowContext(gathered = setOf(logs, oakLogs, rawShrimps), gatheredBy = mapOf(oakLogs to 1, rawShrimps to 2, logs to 3))

        assertEquals(light("$oakLogs,$logs"), kinds.initial(light(), before, InputSource.EARLIER_STEPS))
    }

    @Test
    fun `a new step on the bank starts with none`() {
        assertEquals(light(), kinds.initial(light(), FlowContext(gatheredBy = mapOf(logs to 1)), InputSource.BANK))
    }

    @Test
    fun `the pick names every kind, the first as the cache has it, with its picture`() {
        assertEquals(StepPick("Oak logs, logs", OptionIcon.Item(oakLogs)), kinds.pick(light("$oakLogs,$logs")))
    }

    @Test
    fun `nothing picked shows no label and no picture`() {
        assertEquals(StepPick(null, null), kinds.pick(light()))
    }

    @Test
    fun `the list adds or removes the step's items over four rows`() {
        val field = kinds.field("Logs", OptionSource { emptyList() }, "Which logs would you like to light?")

        assertEquals(listOf("logs", "Logs", "+ Add or remove logs...", "4"), listOf(field.key, field.label, field.add, field.rows.toString()))
    }

    @Test
    fun `nothing picked cannot work`() {
        val error = assertThrows<FlowError> { kinds.resolve(light(), FlowContext(), InputSource.BANK) }

        assertEquals("light needs logs picked", error.message)
    }

    @Test
    fun `a kind no step before gets cannot work on earlier steps`() {
        val error = assertThrows<FlowError> { kinds.resolve(light("$oakLogs"), FlowContext(gathered = setOf(logs)), InputSource.EARLIER_STEPS) }

        assertEquals("no step before gets oak logs", error.message)
    }

    @Test
    fun `the kinds picked are what the step works on`() {
        assertEquals(setOf(logs, oakLogs), kinds.resolve(light("$logs,$oakLogs"), FlowContext(), InputSource.BANK))
    }
}
