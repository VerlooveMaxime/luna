package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FlowParserTest {

    @Test
    fun `a chop step names its tree and location`() {
        assertEquals(FlowStep.Chop("oak", "varrock_west"), FlowParser.parse("chop oak @varrock_west"))
    }

    @Test
    fun `words are matched ignoring case and extra spaces`() {
        assertEquals(FlowStep.Chop("normal", "varrock_west"), FlowParser.parse("  Chop  Normal @Varrock_West   "))
    }

    @Test
    fun `drop`() {
        assertEquals(FlowStep.Drop, FlowParser.parse("drop"))
    }

    @Test
    fun `bank deposit all`() {
        assertEquals(FlowStep.BankDepositAll, FlowParser.parse("bank deposit all"))
    }

    @Test
    fun `an unknown step lists the grammar`() {
        assertRejected("Unknown step 'loop'. Steps: ${FlowParser.HELP}", "loop")
    }

    @Test
    fun `an empty line is an unknown step`() {
        assertRejected("Unknown step ''. Steps: ${FlowParser.HELP}", "")
    }

    @Test
    fun `chop without a tree`() {
        assertRejected("chop needs a tree: chop <tree> @<location>", "chop")
    }

    @Test
    fun `chop with several trees`() {
        assertRejected("One kind of tree per chop step: chop <tree> @<location>", "chop normal,oak @x")
    }

    @Test
    fun `chop without a location`() {
        assertRejected("chop needs a location: chop normal @<location>", "chop normal")
    }

    @Test
    fun `chop with a location missing its at sign`() {
        assertRejected("chop needs a location: chop normal @<location>", "chop normal varrock_west")
    }

    @Test
    fun `chop with an empty location`() {
        assertRejected("chop needs a location: chop normal @<location>", "chop normal @")
    }

    @Test
    fun `the old clauses on a chop step point at what replaced them`() {
        assertRejected(
            "Unexpected 'until inventory full' after the location. A chop step ends when the inventory is full; " +
                "'drop' and 'bank deposit all' are steps of their own, and the flow repeats by itself.",
            "chop normal @x until inventory full",
        )
    }

    @Test
    fun `drop takes nothing`() {
        assertRejected("drop takes nothing after it: it drops what the chop steps before it gathered", "drop logs")
    }

    @Test
    fun `bank only deposits all`() {
        assertRejected("The only bank step is 'bank deposit all'", "bank withdraw axe")
    }

    private fun assertRejected(message: String, line: String) {
        val error = assertThrows<FlowError> { FlowParser.parse(line) }

        assertEquals(message, error.message)
    }
}
