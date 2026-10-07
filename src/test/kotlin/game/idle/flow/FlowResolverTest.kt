package game.idle.flow

import game.idle.flow.FakeStepType.Companion.step
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FlowResolverTest {

    private val walk = FakeStepType("walk")
    private val rest = FakeStepType("rest")
    private val resolver = FlowResolver(StepTypes(listOf(walk, rest)))

    @Test
    fun `each step resolves through its kind of step`() {
        assertEquals(listOf(FakeStep("walk north"), FakeStep("rest")), resolver.resolve(listOf(step("walk", "north"), step("rest"))))
    }

    @Test
    fun `the first step starts from an empty context`() {
        resolver.resolve(listOf(step("walk", "north")))

        assertEquals(listOf(FlowContext()), walk.contexts)
    }

    @Test
    fun `each step relies on what the steps before it set up`() {
        resolver.resolve(listOf(step("walk", "gather"), step("rest"), step("rest")))

        assertEquals(listOf(FlowContext(gathered = setOf(1)), FlowContext(gathered = setOf(1))), rest.contexts)
    }

    @Test
    fun `an empty flow resolves to no steps`() {
        assertEquals(emptyList<ResolvedStep>(), resolver.resolve(emptyList()))
    }

    @Test
    fun `a step of an unknown kind names its step`() {
        assertRejected("Step 2: 'fly' is not a kind of step", listOf(step("walk"), step("fly")))
    }

    @Test
    fun `a step its kind of step refuses names its step`() {
        assertRejected("Step 1: 'bad' is refused", listOf(step("walk", "bad")))
    }

    private fun assertRejected(message: String, steps: List<StepSettings>) {
        val error = assertThrows<FlowError> { resolver.resolve(steps) }

        assertEquals(message, error.message)
    }

    @Test
    fun `the context before a step is what the steps before it set up`() {
        val steps = listOf(step("walk", "gather"), step("rest"), step("rest", "gather"))

        assertEquals(FlowContext(gathered = setOf(1)), resolver.contextBefore(steps, 2))
    }

    @Test
    fun `the first step has an empty context before it`() {
        assertEquals(FlowContext(), resolver.contextBefore(listOf(step("walk", "gather")), 0))
    }

    @Test
    fun `a step that does not resolve adds nothing to the context after it`() {
        val steps = listOf(step("walk", "bad"), step("fly"), step("rest"))

        assertEquals(FlowContext(), resolver.contextBefore(steps, 2))
    }
}
