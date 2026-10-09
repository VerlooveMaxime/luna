package game.idle.flow

import game.idle.flow.FakeStepType.Companion.step
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FlowResolverTest {

    private val walk = FakeStepType("walk")
    private val rest = FakeStepType("rest")
    private val resolver = FlowResolver(StepTypes(listOf(walk, rest)))

    /** What a first step that gathers item 1 sets up. */
    private val gatheredByStep1 = FlowContext(gathered = setOf(1), gatheredBy = mapOf(1 to 1))

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

        assertEquals(listOf(gatheredByStep1, gatheredByStep1), rest.contexts)
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

        assertEquals(gatheredByStep1, resolver.contextBefore(steps, 2))
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

    @Test
    fun `a step that can work has no problem`() {
        assertEquals(listOf(null, null), resolver.problems(listOf(step("walk"), step("rest"))))
    }

    @Test
    fun `a refused step's problem is its kind's message, without its number`() {
        assertEquals(listOf("'bad' is refused"), resolver.problems(listOf(step("walk", "bad"))))
    }

    @Test
    fun `a step of an unknown kind's problem says so`() {
        assertEquals(listOf("'fly' is not a kind of step"), resolver.problems(listOf(step("fly"))))
    }

    @Test
    fun `every step gets its own answer, past a refused one`() {
        assertEquals(listOf("'fly' is not a kind of step", "'bad' is refused", null), resolver.problems(listOf(step("fly"), step("walk", "bad"), step("rest"))))
    }

    @Test
    fun `a refused step adds nothing to what the steps after it rely on`() {
        resolver.problems(listOf(step("walk", "bad"), step("rest")))

        assertEquals(listOf(FlowContext()), rest.contexts)
    }

    @Test
    fun `a step that works sets up what the steps after it rely on`() {
        resolver.problems(listOf(step("walk", "gather"), step("rest")))

        assertEquals(listOf(gatheredByStep1), rest.contexts)
    }

    @Test
    fun `what a step gathers is known to come from it`() {
        val steps = listOf(step("rest"), step("walk", "gather"), step("rest"))

        assertEquals(mapOf(1 to 2), resolver.contextBefore(steps, 2).gatheredBy)
    }

    @Test
    fun `an item gathered again stays with the step that first got it`() {
        val steps = listOf(step("walk", "gather"), step("rest", "gather"), step("rest"))

        assertEquals(mapOf(1 to 1), resolver.contextBefore(steps, 2).gatheredBy)
    }

    @Test
    fun `a resolved flow tells each step where what it relies on comes from`() {
        resolver.resolve(listOf(step("rest"), step("walk", "gather"), step("rest")))

        assertEquals(mapOf(1 to 2), rest.contexts.last().gatheredBy)
    }
}
