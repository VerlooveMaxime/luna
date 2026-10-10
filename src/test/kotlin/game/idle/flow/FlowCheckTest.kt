package game.idle.flow

import game.idle.flow.FakeStepType.Companion.step
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class FlowCheckTest {

    private val check = FlowCheck(FlowResolver(StepTypes(listOf(FakeStepType("walk")))), ReflexResolver(setOf(333)), maxSteps = 2, maxReflexes = 1)

    private fun walk(id: Int) = step("walk").copy(id = id)

    private fun refusal(steps: List<StepSettings>, reflexes: List<ReflexSettings> = emptyList()): String? =
        assertThrows<FlowError> { check.check(steps, reflexes) }.message

    @Test
    fun `a flow within the limits whose steps and reflexes resolve passes`() {
        assertDoesNotThrow { check.check(listOf(walk(1), walk(2).copy(reflexes = listOf(1))), listOf(ReflexSettings(1))) }
    }

    @Test
    fun `a flow with more steps than the limit is refused`() {
        assertEquals("A flow holds 2 steps at most, not 3", refusal(listOf(walk(1), walk(2), walk(3))))
    }

    @Test
    fun `a flow with more reflexes than the limit is refused`() {
        assertEquals("A flow holds 1 reflexes at most, not 2", refusal(listOf(walk(1)), listOf(ReflexSettings(1), ReflexSettings(2))))
    }

    @Test
    fun `two steps sharing an id are refused`() {
        assertEquals("Two of the flow's steps have id 4", refusal(listOf(walk(4), walk(4))))
    }

    @Test
    fun `two reflexes sharing an id are refused`() {
        val check = FlowCheck(FlowResolver(StepTypes(listOf(FakeStepType("walk")))), ReflexResolver(emptySet()), maxSteps = 2, maxReflexes = 2)

        val error = assertThrows<FlowError> { check.check(listOf(walk(1)), listOf(ReflexSettings(3), ReflexSettings(3))) }

        assertEquals("Two of the flow's reflexes have id 3", error.message)
    }

    @Test
    fun `a flow with a wrong step is refused with the resolver's reason`() {
        assertEquals("Step 1: 'bad' is refused", refusal(listOf(step("walk", "bad").copy(id = 1))))
    }

    @Test
    fun `a flow with a wrong reflex is refused with the reflex resolver's reason`() {
        assertEquals("Reflex 1: item 1511 is not something you can eat", refusal(listOf(walk(1)), listOf(ReflexSettings(1, mapOf("foods" to "1511")))))
    }
}
