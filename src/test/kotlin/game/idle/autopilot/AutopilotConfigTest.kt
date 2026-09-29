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
    fun `defaults wait two ticks and look fifteen tiles around`() {
        val config = AutopilotConfig()

        assertEquals(AutopilotConfig(decisionDelayTicks = 2, treeSearchRadius = 15), config)
    }

    @Test
    fun `parse reads snake case keys and ignores comments`() {
        val jsonc = """
            {
              // comment lines are allowed
              "decision_delay_ticks": 5,
              "tree_search_radius": 10
            }
        """

        val config = AutopilotConfig.parse(jsonc)

        assertEquals(AutopilotConfig(decisionDelayTicks = 5, treeSearchRadius = 10), config)
    }

    @Test
    fun `the tracked config file is valid`() {
        assertDoesNotThrow { AutopilotConfig.load(AutopilotConfig.PATH) }
    }

    @Test
    fun `a missing file gives the defaults`(@TempDir dir: Path) {
        val config = AutopilotConfig.load(dir.resolve("autopilot.jsonc"))

        assertEquals(AutopilotConfig(), config)
    }

    @Test
    fun `an existing file is read`(@TempDir dir: Path) {
        val file = Files.writeString(dir.resolve("autopilot.jsonc"), """{ "decision_delay_ticks": 4 }""")

        val config = AutopilotConfig.load(file)

        assertEquals(AutopilotConfig(decisionDelayTicks = 4), config)
    }

    @Test
    fun `a decision delay of zero is rejected`() {
        assertThrows<IllegalArgumentException> { AutopilotConfig(decisionDelayTicks = 0).validated() }
    }

    @Test
    fun `a search radius of zero is rejected`() {
        assertThrows<IllegalArgumentException> { AutopilotConfig(treeSearchRadius = 0).validated() }
    }

    @Test
    fun `a search radius past the maximum is rejected`() {
        val tooFar = AutopilotConfig.MAX_SEARCH_RADIUS + 1

        assertThrows<IllegalArgumentException> { AutopilotConfig(treeSearchRadius = tooFar).validated() }
    }

    @Test
    fun `the maximum search radius is accepted`() {
        val config = AutopilotConfig(treeSearchRadius = AutopilotConfig.MAX_SEARCH_RADIUS)

        assertEquals(config, config.validated())
    }
}
