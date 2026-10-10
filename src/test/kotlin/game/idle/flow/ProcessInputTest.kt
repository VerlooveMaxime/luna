package game.idle.flow

import game.idle.flow.option.FakeNames
import game.idle.flow.option.InputSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class ProcessInputTest {

    private val logs = 1511
    private val oakLogs = 1521
    private val usable = setOf(logs, oakLogs)
    private val chopped = FlowContext(gathered = setOf(logs))
    private val names = FakeNames(items = mapOf(oakLogs to "Oak logs"))

    private fun light(input: String? = null) = StepSettings("light", input?.let { mapOf("input" to it) }.orEmpty())

    @Test
    fun `a step kept on earlier steps takes from them`() {
        assertEquals(InputSource.EARLIER_STEPS, ProcessInput.read(light("earlier"), FlowContext(), usable))
    }

    @Test
    fun `a step kept on the bank takes from it, whatever the steps before get`() {
        assertEquals(InputSource.BANK, ProcessInput.read(light("bank"), chopped, usable))
    }

    @Test
    fun `without a choice a step after one getting what it can use takes from the earlier steps`() {
        assertEquals(InputSource.EARLIER_STEPS, ProcessInput.read(light(), chopped, usable))
    }

    @Test
    fun `without a choice a step with nothing usable before it takes from the bank`() {
        assertEquals(InputSource.BANK, ProcessInput.read(light(), FlowContext(gathered = setOf(317)), usable))
    }

    @Test
    fun `a new step keeps the flow's rule`() {
        assertEquals(light("earlier"), ProcessInput.initial(light(), chopped, usable))
    }

    @Test
    fun `a new step with nothing usable before it starts on the bank`() {
        assertEquals(light("bank"), ProcessInput.initial(light(), FlowContext(), usable))
    }

    @Test
    fun `an input from the bank is never checked against the steps before`() {
        assertDoesNotThrow { ProcessInput.requireGathered(InputSource.BANK, FlowContext(), listOf(oakLogs), names) }
    }

    @Test
    fun `an input the steps before get passes`() {
        assertDoesNotThrow { ProcessInput.requireGathered(InputSource.EARLIER_STEPS, chopped, listOf(logs), names) }
    }

    @Test
    fun `an input no step before gets is named`() {
        val error = assertThrows<FlowError> { ProcessInput.requireGathered(InputSource.EARLIER_STEPS, chopped, listOf(logs, oakLogs), names) }

        assertEquals("no step before gets oak logs", error.message)
    }

    @Test
    fun `the toggle offers earlier steps and the bank`() {
        assertEquals(listOf(Choice("earlier", "Earlier steps"), Choice("bank", "The bank")), ProcessInput.field(usable).choices)
    }

    @Test
    fun `the toggle lights the flow's rule for a step without a choice`() {
        assertEquals("earlier", ProcessInput.field(usable).current(light(), chopped))
    }

    @Test
    fun `the toggle lights the choice kept`() {
        assertEquals("bank", ProcessInput.field(usable).current(light("bank"), chopped))
    }
}
