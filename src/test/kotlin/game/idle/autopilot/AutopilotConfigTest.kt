package game.idle.autopilot

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
        assertEquals(AutopilotConfig(decisionDelayTicks = 2), AutopilotConfig())
    }

    @Test
    fun `parse reads snake case keys and ignores comments`() {
        val jsonc = """
            {
              // comment lines are allowed
              "decision_delay_ticks": 5
            }
        """

        assertEquals(AutopilotConfig(decisionDelayTicks = 5), AutopilotConfig.parse(jsonc))
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
}
