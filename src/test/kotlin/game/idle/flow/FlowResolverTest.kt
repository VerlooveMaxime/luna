package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FlowResolverTest {

    private val walk = FakeStepType("walk")
    private val rest = FakeStepType("rest")
    private val resolver = FlowResolver(FlowGrammar(listOf(walk, rest)))

    @Test
    fun `each line resolves through its kind of step`() {
        assertEquals(listOf(FakeStep("walk north"), FakeStep("rest")), resolver.resolve(listOf("walk north", "rest")))
    }

    @Test
    fun `the first step starts from an empty context`() {
        resolver.resolve(listOf("walk north"))

        assertEquals(listOf(FlowContext()), walk.contexts)
    }

    @Test
    fun `each step relies on what the steps before it set up`() {
        resolver.resolve(listOf("walk gather", "rest", "rest"))

        assertEquals(listOf(FlowContext(gathered = setOf(1)), FlowContext(gathered = setOf(1))), rest.contexts)
    }

    @Test
    fun `an empty flow resolves to no steps`() {
        assertEquals(emptyList<ResolvedStep>(), resolver.resolve(emptyList()))
    }

    @Test
    fun `a line that does not parse names its step`() {
        assertRejected("Step 2: rest takes one word at most", listOf("walk", "rest a b"))
    }

    @Test
    fun `a line its kind of step refuses names its step`() {
        assertRejected("Step 1: 'bad' is refused", listOf("walk bad"))
    }

    private fun assertRejected(message: String, lines: List<String>) {
        val error = assertThrows<FlowError> { resolver.resolve(lines) }

        assertEquals(message, error.message)
    }
}
