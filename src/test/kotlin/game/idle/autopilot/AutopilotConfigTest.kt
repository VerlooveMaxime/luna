package game.idle.autopilot

import game.idle.ui.FlowWidgets
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AutopilotConfigTest {

    @Test
    fun `the default decision delay is two ticks`() {
        assertEquals(AutopilotConfig(decisionDelayTicks = 2, stepSlots = 4, savedFlowSlots = 2), AutopilotConfig())
    }

    @Test
    fun `parse reads snake case keys and ignores comments`() {
        val jsonc = """
            {
              // comment lines are allowed
              "decision_delay_ticks": 5,
              "step_slots": 6,
              "saved_flow_slots": 3
            }
        """

        assertEquals(AutopilotConfig(decisionDelayTicks = 5, stepSlots = 6, savedFlowSlots = 3), AutopilotConfig.parse(jsonc))
    }

    @Test
    fun `the tracked config file is valid`() {
        assertDoesNotThrow { AutopilotConfig.load(AutopilotConfig.PATH) }
    }

    @Test
    fun `a missing file gives the defaults`(@TempDir dir: Path) {
        assertEquals(AutopilotConfig(), AutopilotConfig.load(dir.resolve("autopilot.jsonc")))
    }

    @Test
    fun `an existing file is read`(@TempDir dir: Path) {
        val file = Files.writeString(dir.resolve("autopilot.jsonc"), """{ "decision_delay_ticks": 4 }""")

        assertEquals(AutopilotConfig(decisionDelayTicks = 4), AutopilotConfig.load(file))
    }

    @Test
    fun `a decision delay of zero is rejected`() {
        assertThrows<IllegalArgumentException> { AutopilotConfig(decisionDelayTicks = 0).validated() }
    }

    @Test
    fun `a flow without step slots is rejected`() {
        assertThrows<IllegalArgumentException> { AutopilotConfig(stepSlots = 0).validated() }
    }

    @Test
    fun `no saved-flow slots is rejected`() {
        assertThrows<IllegalArgumentException> { AutopilotConfig(savedFlowSlots = 0).validated() }
    }

    @Test
    fun `more saved-flow slots than the Idle tab has ids for are rejected`() {
        assertThrows<IllegalArgumentException> { AutopilotConfig(savedFlowSlots = FlowWidgets.MOST_SAVED_SLOTS + 1).validated() }
    }

    @Test
    fun `as many saved-flow slots as the Idle tab has ids for are accepted`() {
        assertEquals(FlowWidgets.MOST_SAVED_SLOTS, AutopilotConfig(savedFlowSlots = FlowWidgets.MOST_SAVED_SLOTS).validated().savedFlowSlots)
    }
}
