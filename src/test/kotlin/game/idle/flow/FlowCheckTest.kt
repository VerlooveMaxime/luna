package game.idle.flow

import game.idle.flow.FakeStepType.Companion.step
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class FlowCheckTest {

    private val check = FlowCheck(FlowResolver(StepTypes(listOf(FakeStepType("walk")))), maxSteps = 2)

    @Test
    fun `a flow within the step limit whose steps resolve passes`() {
        assertDoesNotThrow { check.check(listOf(step("walk"), step("walk"))) }
    }

    @Test
    fun `a flow with more steps than the limit is refused`() {
        val error = assertThrows<FlowError> { check.check(listOf(step("walk"), step("walk"), step("walk"))) }

        assertEquals("A flow holds 2 steps at most, not 3", error.message)
    }

    @Test
    fun `a flow with a wrong step is refused with the resolver's reason`() {
        val error = assertThrows<FlowError> { check.check(listOf(step("walk", "bad"))) }

        assertEquals("Step 1: 'bad' is refused", error.message)
    }
}
