package game.idle.autopilot.firemaking

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepIcon
import game.idle.flow.StepPick
import game.idle.flow.StepSettings
import game.idle.flow.described
import game.idle.flow.option.FakeNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.OptionIcon
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LightStepTypeTest {

    private val logs = 1511
    private val oakLogs = 1521
    private val rawShrimps = 317
    private val type = LightStepType(FakeNames(items = mapOf(logs to "Logs", oakLogs to "Oak logs")))
    private val chopped = FlowContext(gathered = setOf(logs), gatheredBy = mapOf(logs to 1))

    private fun light(vararg values: Pair<String, String>) = StepSettings("light", mapOf(*values))

    @Test
    fun `a light step with nothing picked reads as light alone`() {
        assertEquals("light", type.summary(light()))
    }

    @Test
    fun `a light step's summary names its count and its logs`() {
        assertEquals("light 2 logs, oak logs", type.summary(light("amount" to "2", "logs" to "$logs,$oakLogs")))
    }

    @Test
    fun `light lights the logs picked, from the steps before it`() {
        val step = type.resolve(light("amount" to "1", "input" to "earlier", "logs" to "$logs"), chopped)

        assertEquals(LightStep(setOf(logs), amount = 1), step)
    }

    @Test
    fun `light from the bank needs no step before it`() {
        assertEquals(LightStep(setOf(oakLogs)), type.resolve(light("input" to "bank", "logs" to "$oakLogs"), FlowContext()))
    }

    @Test
    fun `items that are no logs are left out`() {
        assertEquals(LightStep(setOf(logs)), type.resolve(light("input" to "bank", "logs" to "$rawShrimps,$logs"), FlowContext()))
    }

    @Test
    fun `light with no logs picked cannot work`() {
        val error = assertThrows<FlowError> { type.resolve(light("input" to "bank"), FlowContext()) }

        assertEquals("light needs logs picked", error.message)
    }

    @Test
    fun `light on earlier steps cannot light logs no step before gets`() {
        val error = assertThrows<FlowError> { type.resolve(light("input" to "earlier", "logs" to "$oakLogs"), chopped) }

        assertEquals("no step before gets oak logs", error.message)
    }

    @Test
    fun `the configure screen toggles the input, types the amount and lists the logs`() {
        assertEquals(
            listOf(
                "Input (left): toggle input earlier 'Earlier steps' / bank 'The bank'",
                "Amount (left): typed amount 1..1000, button 'All'",
                "Logs (left): list logs on 4 rows, 'Which logs would you like to light?'",
            ),
            described(type.fields(FakeNames())),
        )
    }

    @Test
    fun `a new light step after a chop step lights its logs`() {
        assertEquals(light("input" to "earlier", "logs" to "$logs"), type.newSettings(chopped))
    }

    @Test
    fun `a new light step alone takes its logs from the bank, none picked yet`() {
        assertEquals(light("input" to "bank"), type.newSettings(FlowContext()))
    }

    @Test
    fun `a light step's input follows the flow until one is kept`() {
        assertEquals(InputSource.EARLIER_STEPS, type.input(light(), chopped))
    }

    @Test
    fun `a light step shows the logs it lights`() {
        assertEquals(StepPick("Logs", OptionIcon.Item(logs)), type.pick(light("logs" to "$logs"), FakeNames()))
    }

    @Test
    fun `the configure screen shows the Firemaking level`() {
        assertEquals(Skill.FIREMAKING, type.skill(light()))
    }

    @Test
    fun `a light step shows the Firemaking icon`() {
        assertEquals(StepIcon.Skill(Skill.FIREMAKING), type.icon(StepSettings("light")))
    }

    @Test
    fun `a light step's slot names the step its logs come from, all of them`() {
        assertEquals(listOf("from step 1", "all of them"), type.details(light("input" to "earlier", "logs" to "$logs"), chopped))
    }

    @Test
    fun `a light step's slot says when its logs come from the bank, and how many a lap lights`() {
        assertEquals(listOf("from the bank", "5 per lap"), type.details(light("input" to "bank", "amount" to "5"), chopped))
    }

    @Test
    fun `a light step has no target to search`() {
        assertNull(type.target(FakeNames()))
    }
}
