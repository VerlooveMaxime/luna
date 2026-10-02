package game.idle.ui

import game.idle.IdleState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AutopilotStatusTest {

    private val flow = listOf("chop oak @draynor_oaks until inventory full", "bank deposit all", "loop")

    @Test
    fun `nothing is shown while the autopilot is off`() {
        assertEquals("", AutopilotStatus.text(IdleState(flow = flow, stepIndex = 1, running = false)))
    }

    @Test
    fun `a running flow shows the step number and its line`() {
        val text = AutopilotStatus.text(IdleState(flow = flow, stepIndex = 1, running = true))

        assertEquals("@gre@Autopilot@whi@ step 2/3|@yel@bank deposit all", text)
    }

    @Test
    fun `a flow past its last step shows the header alone`() {
        val text = AutopilotStatus.text(IdleState(flow = flow, stepIndex = 3, running = true))

        assertEquals("@gre@Autopilot@whi@ step 4/3", text)
    }

    @Test
    fun `separators typed in a flow line become spaces`() {
        val text = AutopilotStatus.text(IdleState(flow = listOf("chop oak|willow\n@here"), stepIndex = 0, running = true))

        assertEquals("@gre@Autopilot@whi@ step 1/1|@yel@chop oak willow @here", text)
    }

    @Test
    fun `the tab says off with two blank lines while the autopilot is off`() {
        assertEquals(listOf("Autopilot: off", "", ""), AutopilotStatus.tabLines(IdleState(flow = flow)))
    }

    @Test
    fun `the tab shows the step number and its line while running`() {
        val lines = AutopilotStatus.tabLines(IdleState(flow = flow, stepIndex = 1, running = true))

        assertEquals(listOf("Autopilot: running", "Step 2/3", "bank deposit all"), lines)
    }

    @Test
    fun `the tab shortens a long step line`() {
        val lines = AutopilotStatus.tabLines(IdleState(flow = flow, stepIndex = 0, running = true))

        assertEquals("chop oak @draynor_oaks unt..", lines[2])
    }

    @Test
    fun `the tab shows no line past the last step`() {
        assertEquals("", AutopilotStatus.tabLines(IdleState(flow = flow, stepIndex = 3, running = true))[2])
    }
}
