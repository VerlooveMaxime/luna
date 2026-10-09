package game.idle.autopilot.firemaking

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LightStepTypeTest {

    private val logs = 1511
    private val oakLogs = 1521
    private val rawShrimps = 317

    private fun light(vararg values: Pair<String, String>) = StepSettings("light", mapOf(*values))

    @Test
    fun `a light step without an amount lights every log`() {
        assertEquals("light", LightStepType.summary(light()))
    }

    @Test
    fun `a light step with an amount lights that many`() {
        assertEquals("light 2", LightStepType.summary(light("amount" to "2")))
    }

    @Test
    fun `light lights the logs the steps before it gathered, nothing else`() {
        val step = LightStepType.resolve(light("amount" to "1"), FlowContext(gathered = setOf(logs, oakLogs, rawShrimps)))

        assertEquals(LightStep(setOf(logs, oakLogs), amount = 1), step)
    }

    @Test
    fun `light with no logs gathered before it is rejected`() {
        val error = assertThrows<FlowError> { LightStepType.resolve(light(), FlowContext(gathered = setOf(rawShrimps))) }

        assertEquals("light comes after a chop step, so the flow knows which logs to light", error.message)
    }

    @Test
    fun `the builder offers all or a few counts`() {
        val field = LightStepType.fields[0] as StepField.Choice

        assertEquals(listOf("", "1", "5", "10"), field.choices(light()))
        assertEquals("all", field.display(""))
    }

    @Test
    fun `a light step shows the Firemaking icon`() {
        assertEquals(StepIcon.Skill(Skill.FIREMAKING), LightStepType.icon(StepSettings("light")))
    }
}
