package game.idle.ui

import game.idle.IdleState
import game.idle.flow.FakeStepType
import game.idle.flow.FlowResolver
import game.idle.flow.StepLines
import game.idle.flow.StepSettings
import game.idle.flow.StepTypes
import game.idle.flow.option.FakeNames
import game.idle.font.EVEN_FONT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AutopilotStatusTest {

    /** A kind whose slot says what its `text` setting holds. */
    private val line = FakeStepType("line", detailsOf = { settings, _ -> listOf(settings["text"].orEmpty()) })
    private val flow = listOf("chop oak until the bag is full", "bank deposit all", "loop").map(::line)
    private val status = AutopilotStatus(StepLines(FlowResolver(StepTypes(listOf(line))), FakeNames()), EVEN_FONT)

    private fun line(text: String) = StepSettings("line", mapOf("text" to text))

    private fun last(state: IdleState): String = status.text(state).split(StatusOverlayMessageWriter.LINE_SEPARATOR).last()

    @Test
    fun `nothing is shown while the autopilot is off`() {
        assertEquals("", status.text(IdleState(steps = flow, stepIndex = 1, running = false)))
    }

    @Test
    fun `a running flow shows the step number and the step as its slot words it`() {
        val text = status.text(IdleState(steps = flow, stepIndex = 1, running = true))

        assertEquals("@gre@Autopilot@whi@ step 2/3|@yel@Line label: bank deposit all", text)
    }

    @Test
    fun `a flow past its last step shows the header alone`() {
        val text = status.text(IdleState(steps = flow, stepIndex = 3, running = true))

        assertEquals("@gre@Autopilot@whi@ step 4/3", text)
    }

    @Test
    fun `a step line too wide for the overlay is cut`() {
        val state = IdleState(steps = listOf(line("x".repeat(120))), stepIndex = 0, running = true)

        assertEquals("@yel@Line label: " + "x".repeat(86) + "..", last(state))
    }

    @Test
    fun `separators in a step's words become spaces`() {
        val text = status.text(IdleState(steps = listOf(line("chop oak|willow\n@here")), stepIndex = 0, running = true))

        assertEquals("@gre@Autopilot@whi@ step 1/1|@yel@Line label: chop oak willow @here", text)
    }

    @Test
    fun `a blocked step adds why in red, as it is`() {
        val state = IdleState(steps = flow, stepIndex = 0, running = true, blocked = "Needs an axe you can use")

        assertEquals("@red@Needs an axe you can use", last(state))
    }

    @Test
    fun `a waiting step adds what it waits for in yellow`() {
        val state = IdleState(steps = flow, stepIndex = 0, running = true, waiting = "No tree in reach")

        assertEquals("@yel@No tree in reach", last(state))
    }

    @Test
    fun `a step on its way adds what it does in white`() {
        val state = IdleState(steps = flow, stepIndex = 0, running = true, underWay = "Opening the door")

        assertEquals("@whi@Opening the door", last(state))
    }

    @Test
    fun `what a step does on its way comes under its line, before a block`() {
        val state = IdleState(steps = flow, stepIndex = 0, running = true, underWay = "Opening the door", blocked = "No way there from here")

        assertEquals(listOf("@whi@Opening the door", "@red@No way there from here"), status.text(state).split(StatusOverlayMessageWriter.LINE_SEPARATOR).drop(2))
    }

    @Test
    fun `separators in a block become spaces`() {
        val state = IdleState(steps = flow, stepIndex = 0, running = true, blocked = "No|way\nhere")

        assertEquals("@red@No way here", last(state))
    }
}
