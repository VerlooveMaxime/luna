package game.idle.autopilot

import game.idle.ui.BuilderWidgets
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
    fun `the default decision delay is one to three ticks`() {
        assertEquals(AutopilotConfig(minDecisionDelayTicks = 1, maxDecisionDelayTicks = 3, stepSlots = 4, savedFlowSlots = 2, reflexSlots = 4), AutopilotConfig())
    }

    @Test
    fun `the decision delay is the range between its two lengths`() {
        assertEquals(2..5, AutopilotConfig(minDecisionDelayTicks = 2, maxDecisionDelayTicks = 5).decisionDelayTicks)
    }

    @Test
    fun `parse reads snake case keys and ignores comments`() {
        val jsonc = """
            {
              // comment lines are allowed
              "min_decision_delay_ticks": 5,
              "max_decision_delay_ticks": 8,
              "step_slots": 6,
              "saved_flow_slots": 3,
              "reflex_slots": 7
            }
        """

        assertEquals(AutopilotConfig(minDecisionDelayTicks = 5, maxDecisionDelayTicks = 8, stepSlots = 6, savedFlowSlots = 3, reflexSlots = 7), AutopilotConfig.parse(jsonc))
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
        val file = Files.writeString(dir.resolve("autopilot.jsonc"), """{ "max_decision_delay_ticks": 4 }""")

        assertEquals(AutopilotConfig(maxDecisionDelayTicks = 4), AutopilotConfig.load(file))
    }

    @Test
    fun `a decision delay of zero is rejected`() {
        assertThrows<IllegalArgumentException> { AutopilotConfig(minDecisionDelayTicks = 0).validated() }
    }

    @Test
    fun `a longest decision delay shorter than the shortest is rejected`() {
        assertThrows<IllegalArgumentException> { AutopilotConfig(minDecisionDelayTicks = 3, maxDecisionDelayTicks = 2).validated() }
    }

    @Test
    fun `a flow without step slots is rejected`() {
        assertThrows<IllegalArgumentException> { AutopilotConfig(stepSlots = 0).validated() }
    }

    @Test
    fun `more step slots than the builder has ids for are rejected`() {
        assertThrows<IllegalArgumentException> { AutopilotConfig(stepSlots = BuilderWidgets.MOST_STEP_SLOTS + 1).validated() }
    }

    @Test
    fun `more reflex slots than the builder has rows for are rejected`() {
        assertThrows<IllegalArgumentException> { AutopilotConfig(reflexSlots = BuilderWidgets.MOST_REFLEX_SLOTS + 1).validated() }
    }

    @Test
    fun `as many step and reflex slots as the builder has room for are accepted`() {
        val most = AutopilotConfig(stepSlots = BuilderWidgets.MOST_STEP_SLOTS, reflexSlots = BuilderWidgets.MOST_REFLEX_SLOTS)

        assertEquals(most, most.validated())
    }

    @Test
    fun `a flow without reflex slots is rejected`() {
        assertThrows<IllegalArgumentException> { AutopilotConfig(reflexSlots = 0).validated() }
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
