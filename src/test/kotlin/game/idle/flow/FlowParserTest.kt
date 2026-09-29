package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FlowParserTest {

    @Test
    fun `a chop step with everything`() {
        val step = FlowParser.parse("chop normal,oak @varrock_west drop until 100 logs")

        assertEquals(FlowStep.Chop(listOf("normal", "oak"), "varrock_west", drop = true, until = Until.Logs(100)), step)
    }

    @Test
    fun `a bare chop step runs until stopped and keeps its logs`() {
        val step = FlowParser.parse("chop normal @varrock_west")

        assertEquals(FlowStep.Chop(listOf("normal"), "varrock_west", drop = false, until = null), step)
    }

    @Test
    fun `words are matched ignoring case and extra spaces`() {
        val step = FlowParser.parse("  Chop  Normal @Varrock_West   Until Inventory Full ")

        assertEquals(FlowStep.Chop(listOf("normal"), "varrock_west", drop = false, until = Until.InventoryFull), step)
    }

    @Test
    fun `until level`() {
        assertEquals(Until.Level(30), (FlowParser.parse("chop normal @x until level 30") as FlowStep.Chop).until)
    }

    @Test
    fun `bank deposit all`() {
        assertEquals(FlowStep.BankDepositAll, FlowParser.parse("bank deposit all"))
    }

    @Test
    fun `loop`() {
        assertEquals(FlowStep.Loop, FlowParser.parse("loop"))
    }

    @Test
    fun `an unknown step lists the grammar`() {
        assertRejected("Unknown step 'dance'. Steps: ${FlowParser.HELP}", "dance")
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
    fun `chop with only commas for a tree`() {
        assertRejected("chop needs a tree: chop <tree> @<location>", "chop ,")
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
    fun `chop with junk after the location`() {
        assertRejected("Unexpected 'fast' after the location. Expected 'drop' or 'until'.", "chop normal @x fast")
    }

    @Test
    fun `an unknown condition`() {
        assertRejected("Unknown condition 'until dark'. Conditions: inventory full, <n> logs, level <n>", "chop normal @x until dark")
        assertRejected("Unknown condition 'until 5 trees'. Conditions: inventory full, <n> logs, level <n>", "chop normal @x until 5 trees")
    }

    @Test
    fun `a log count must be a positive number`() {
        assertRejected("'zero' is not a number of logs", "chop normal @x until zero logs")
        assertRejected("'0' is not a number of logs", "chop normal @x until 0 logs")
    }

    @Test
    fun `a level must be from one to ninety nine`() {
        assertRejected("'100' is not a level from 1 to 99", "chop normal @x until level 100")
        assertRejected("'ten' is not a level from 1 to 99", "chop normal @x until level ten")
        assertRejected("'0' is not a level from 1 to 99", "chop normal @x until level 0")
    }

    @Test
    fun `bank only deposits all`() {
        assertRejected("The only bank step is 'bank deposit all'", "bank withdraw axe")
    }

    @Test
    fun `loop takes nothing`() {
        assertRejected("loop takes nothing after it", "loop 3")
    }

    private fun assertRejected(message: String, line: String) {
        val error = assertThrows<FlowError> { FlowParser.parse(line) }

        assertEquals(message, error.message)
    }
}
