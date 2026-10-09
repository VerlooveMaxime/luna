package game.idle.ui

import game.idle.IdleState
import game.idle.flow.StepSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AutopilotStatusTest {

    private val flow = listOf("chop oak @draynor_oaks until inventory full", "bank deposit all", "loop").map(::line)
    private val status = AutopilotStatus { it["text"].orEmpty() }

    private fun line(text: String) = StepSettings("line", mapOf("text" to text))

    @Test
    fun `nothing is shown while the autopilot is off`() {
        assertEquals("", status.text(IdleState(steps = flow, stepIndex = 1, running = false)))
    }

    @Test
    fun `a running flow shows the step number and its line`() {
        val text = status.text(IdleState(steps = flow, stepIndex = 1, running = true))

        assertEquals("@gre@Autopilot@whi@ step 2/3|@yel@bank deposit all", text)
    }

    @Test
    fun `a flow past its last step shows the header alone`() {
        val text = status.text(IdleState(steps = flow, stepIndex = 3, running = true))

        assertEquals("@gre@Autopilot@whi@ step 4/3", text)
    }

    @Test
    fun `separators in a step's words become spaces`() {
        val text = status.text(IdleState(steps = listOf(line("chop oak|willow\n@here")), stepIndex = 0, running = true))

        assertEquals("@gre@Autopilot@whi@ step 1/1|@yel@chop oak willow @here", text)
    }

    @Test
    fun `a blocked step adds why in red, without the chat line's prefix`() {
        val state = IdleState(steps = flow, stepIndex = 0, running = true, blocked = "Autopilot: you need an axe.")

        assertEquals("@red@You need an axe.", status.text(state).split(StatusOverlayMessageWriter.LINE_SEPARATOR).last())
    }

    @Test
    fun `separators in a block become spaces`() {
        val state = IdleState(steps = flow, stepIndex = 0, running = true, blocked = "Autopilot: no|way\nhere")

        assertEquals("@red@No way here", status.text(state).split(StatusOverlayMessageWriter.LINE_SEPARATOR).last())
    }

    @Test
    fun `a block that is not a chat line shows as it is`() {
        assertEquals("Out of reach", AutopilotStatus.reason("Out of reach"))
    }

    @Test
    fun `the tab says off with two blank lines while the autopilot is off`() {
        assertEquals(listOf("Autopilot: off", "", ""), status.tabLines(IdleState(steps = flow)))
    }

    @Test
    fun `the tab shows the step number and its line while running`() {
        val lines = status.tabLines(IdleState(steps = flow, stepIndex = 1, running = true))

        assertEquals(listOf("Autopilot: running", "Step 2/3", "bank deposit all"), lines)
    }

    @Test
    fun `the tab shortens a long step line`() {
        val lines = status.tabLines(IdleState(steps = flow, stepIndex = 0, running = true))

        assertEquals("chop oak @draynor_oaks unt..", lines[2])
    }

    @Test
    fun `the tab shows no line past the last step`() {
        assertEquals("", status.tabLines(IdleState(steps = flow, stepIndex = 3, running = true))[2])
    }
}
