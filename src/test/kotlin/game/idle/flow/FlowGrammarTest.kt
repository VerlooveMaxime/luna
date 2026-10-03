package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FlowGrammarTest {

    private val walk = FakeStepType("walk")
    private val rest = FakeStepType("rest")
    private val grammar = FlowGrammar(listOf(walk, rest))

    @Test
    fun `a line goes to the kind of step its first word names`() {
        assertEquals(FlowStep(rest, listOf("here")), grammar.parse("rest here"))
    }

    @Test
    fun `words are matched ignoring case and extra spaces`() {
        assertEquals(FlowStep(walk, listOf("north")), grammar.parse("  Walk   NORTH  "))
    }

    @Test
    fun `the help lists every kind of step in order`() {
        assertEquals("walk [<word>], rest [<word>]", grammar.help)
    }

    @Test
    fun `an unknown step lists the grammar`() {
        assertRejected("Unknown step 'fly away'. Steps: walk [<word>], rest [<word>]", "fly away")
    }

    @Test
    fun `an empty line is an unknown step`() {
        assertRejected("Unknown step ''. Steps: walk [<word>], rest [<word>]", "")
    }

    @Test
    fun `a kind of step refuses its own bad lines`() {
        assertRejected("walk takes one word at most", "walk north fast")
    }

    @Test
    fun `a parsed step reads back as its line`() {
        assertEquals("walk north", grammar.parse("WALK north").line())
    }

    @Test
    fun `a grammar needs a kind of step`() {
        assertThrows<IllegalArgumentException> { FlowGrammar(emptyList()) }
    }

    @Test
    fun `two kinds of step cannot share a keyword`() {
        assertThrows<IllegalArgumentException> { FlowGrammar(listOf(walk, FakeStepType("walk"))) }
    }

    private fun assertRejected(message: String, line: String) {
        val error = assertThrows<FlowError> { grammar.parse(line) }

        assertEquals(message, error.message)
    }
}
