package game.idle.autopilot.firemaking

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.option.FakeNames
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

        assertEquals("light needs logs from a step before it", error.message)
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

    @Test
    fun `a light step's slot names the step its logs come from, all of them`() {
        assertEquals(listOf("Logs from step 2", "all of them"), LightStepType.details(light(), FlowContext(gatheredBy = mapOf(1521 to 2))))
    }

    @Test
    fun `a light step's slot leaves out what is no log`() {
        assertEquals("Logs from step 1", LightStepType.details(light(), FlowContext(gatheredBy = mapOf(1511 to 1, 317 to 2))).first())
    }

    @Test
    fun `a light step's slot says how many logs a lap lights`() {
        assertEquals("5 per lap", LightStepType.details(light("amount" to "5"), FlowContext()).last())
    }

    @Test
    fun `a light step has no target yet`() {
        assertNull(LightStepType.target(FakeNames()))
    }
}
