package game.idle.autopilot.rest

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepNeeds
import game.idle.flow.StepSettings
import game.idle.flow.described
import game.idle.flow.option.FakeNames
import game.idle.flow.option.InputSource
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RestStepTypeTest {

    private val rest = StepSettings("rest")

    private fun rest(percent: String) = StepSettings("rest", mapOf("hitpoints" to percent))

    @Test
    fun `the configure screen types the percent until which it rests`() {
        val fields = RestStepType.fields(FakeNames())

        assertEquals(listOf("Until (left): typed hitpoints 1..100"), described(fields))
    }

    @Test
    fun `the until box shows the percent kept, full without one`() {
        val until = RestStepType.fields(FakeNames()).single() as StepField.Typed

        assertEquals(listOf("100% hitpoints", "70% hitpoints"), listOf(until.shown(null), until.shown("70")))
    }

    @Test
    fun `a rest step rests to full without a percent`() {
        assertEquals(RestStep(100), RestStepType.resolve(rest, FlowContext()))
    }

    @Test
    fun `a rest step rests to its percent`() {
        assertEquals(RestStep(1), RestStepType.resolve(rest("1"), FlowContext()))
    }

    @Test
    fun `a percent that is no number cannot work`() {
        val error = assertThrows<FlowError> { RestStepType.resolve(rest("half"), FlowContext()) }

        assertEquals("Until takes 1 to 100 percent, not 'half'", error.message)
    }

    @Test
    fun `a percent below 1 cannot work`() {
        assertThrows<FlowError> { RestStepType.resolve(rest("0"), FlowContext()) }
    }

    @Test
    fun `a percent above 100 cannot work`() {
        assertThrows<FlowError> { RestStepType.resolve(rest("101"), FlowContext()) }
    }

    @Test
    fun `a rest step shows the hitpoints skill icon`() {
        assertEquals(StepIcon.Skill(Skill.HITPOINTS), RestStepType.icon(rest))
    }

    @Test
    fun `a rest step's slot says until when it rests`() {
        assertEquals(listOf("until 70% hitpoints"), RestStepType.details(rest("70"), FlowContext()))
    }

    @Test
    fun `a rest step says what it does`() {
        assertEquals("Waits where it stands until hitpoints are back up to a share of full.", RestStepType.description)
    }

    @Test
    fun `a rest step shows no level and picks nothing`() {
        assertEquals(listOf(null, null), listOf(RestStepType.skill(rest), RestStepType.pick(rest, FakeNames())))
    }

    @Test
    fun `a new rest step starts with no setting`() {
        assertEquals(StepSettings("rest"), RestStepType.newSettings(FlowContext()))
    }

    @Test
    fun `a rest step needs nothing and changes nothing for the steps after it`() {
        val context = FlowContext(gathered = setOf(1511))

        assertEquals(emptyList<StepNeeds>() to context, RestStep(100).needs() to RestStep(100).after(context))
    }

    @Test
    fun `a rest step has no target and its searches look at the bank`() {
        assertEquals(listOf(null, InputSource.BANK), listOf(RestStepType.target(FakeNames()), RestStepType.input(rest, FlowContext())))
    }
}
