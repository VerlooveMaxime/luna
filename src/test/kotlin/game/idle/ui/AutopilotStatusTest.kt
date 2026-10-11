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
    fun `a blocked step adds why in red, as it is`() {
        val state = IdleState(steps = flow, stepIndex = 0, running = true, blocked = "Needs an axe you can use")

        assertEquals("@red@Needs an axe you can use", status.text(state).split(StatusOverlayMessageWriter.LINE_SEPARATOR).last())
    }

    @Test
    fun `separators in a block become spaces`() {
        val state = IdleState(steps = flow, stepIndex = 0, running = true, blocked = "No|way\nhere")

        assertEquals("@red@No way here", status.text(state).split(StatusOverlayMessageWriter.LINE_SEPARATOR).last())
    }
}
