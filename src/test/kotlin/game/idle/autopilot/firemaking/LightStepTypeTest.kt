package game.idle.autopilot.firemaking

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LightStepTypeTest {

    private val logs = 1511
    private val oakLogs = 1521
    private val rawShrimps = 317

    @Test
    fun `light alone lights every log`() {
        assertEquals(listOf("all"), LightStepType.parse(emptyList()))
        assertEquals("light", LightStepType.line(listOf("all")))
    }

    @Test
    fun `light with a count lights that many`() {
        assertEquals(listOf("2"), LightStepType.parse(listOf("2")))
        assertEquals("light 2", LightStepType.line(listOf("2")))
    }

    @Test
    fun `light takes nothing but a count`() {
        val error = assertThrows<FlowError> { LightStepType.parse(listOf("2", "logs")) }

        assertEquals("light takes only a count: light [<n>]. It lights the logs the steps before it gathered", error.message)
    }

    @Test
    fun `light lights the logs the steps before it gathered, nothing else`() {
        val step = LightStepType.resolve(listOf("1"), FlowContext(gathered = setOf(logs, oakLogs, rawShrimps)))

        assertEquals(LightStep(setOf(logs, oakLogs), amount = 1), step)
    }

    @Test
    fun `light with no logs gathered before it is rejected`() {
        val error = assertThrows<FlowError> { LightStepType.resolve(listOf("all"), FlowContext(gathered = setOf(rawShrimps))) }

        assertEquals("light comes after a chop step, so the flow knows which logs to light", error.message)
    }

    @Test
    fun `the builder offers all or a few counts`() {
        assertEquals(listOf("all", "1", "5", "10"), LightStepType.fields[0].choices(listOf("")))
    }
}
