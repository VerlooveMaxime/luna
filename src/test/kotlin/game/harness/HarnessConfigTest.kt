package game.harness

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class HarnessConfigTest {

    @Test
    fun `defaults keep the harness off on loopback port 43595`() {
        val config = HarnessConfig()

        assertFalse(config.enabled)
        assertEquals("127.0.0.1", config.bindAddress)
        assertEquals(43595, config.port)
    }

    @Test
    fun `parse reads snake case keys and ignores comments`() {
        val jsonc = """
            {
              // comment lines are allowed
              "enabled": true,
              "bind_address": "localhost",
              "player_name_prefix": "test_"
            }
        """

        val config = HarnessConfig.parse(jsonc)

        assertEquals(HarnessConfig(enabled = true, bindAddress = "localhost", playerNamePrefix = "test_"), config)
    }

    @Test
    fun `parse keeps defaults for missing keys`() {
        val config = HarnessConfig.parse("""{ "port": 50000 }""")

        assertEquals(HarnessConfig(port = 50000), config)
    }

    @Test
    fun `request timeout is read in milliseconds`() {
        val config = HarnessConfig(requestTimeoutMillis = 2500)

        assertEquals(Duration.ofMillis(2500), config.requestTimeout)
    }

    @Test
    fun `environment true enables a disabled config`() {
        val config = HarnessConfig(enabled = false).withEnvironment(mapOf(HarnessConfig.ENABLED_VARIABLE to "true"))

        assertTrue(config.enabled)
    }

    @Test
    fun `environment 1 enables a disabled config`() {
        val config = HarnessConfig(enabled = false).withEnvironment(mapOf(HarnessConfig.ENABLED_VARIABLE to " 1 "))

        assertTrue(config.enabled)
    }

    @Test
    fun `environment false disables an enabled config`() {
        val config = HarnessConfig(enabled = true).withEnvironment(mapOf(HarnessConfig.ENABLED_VARIABLE to "FALSE"))

        assertFalse(config.enabled)
    }

    @Test
    fun `environment 0 disables an enabled config`() {
        val config = HarnessConfig(enabled = true).withEnvironment(mapOf(HarnessConfig.ENABLED_VARIABLE to "0"))

        assertFalse(config.enabled)
    }

    @Test
    fun `environment without the variable keeps the file value`() {
        val config = HarnessConfig(enabled = true).withEnvironment(mapOf("OTHER" to "false"))

        assertTrue(config.enabled)
    }

    @Test
    fun `environment with an unknown value is rejected`() {
        val environment = mapOf(HarnessConfig.ENABLED_VARIABLE to "yes")

        assertThrows<IllegalArgumentException> { HarnessConfig().withEnvironment(environment) }
    }

    @Test
    fun `validated accepts the defaults`() {
        val config = HarnessConfig()

        assertEquals(config, config.validated())
    }

    @Test
    fun `validated rejects a bind address beyond loopback`() {
        val config = HarnessConfig(bindAddress = "0.0.0.0")

        assertThrows<IllegalArgumentException> { config.validated() }
    }

    @Test
    fun `validated rejects a port out of range`() {
        val config = HarnessConfig(port = 70000)

        assertThrows<IllegalArgumentException> { config.validated() }
    }

    @Test
    fun `validated rejects a name prefix that leaves no room for a name`() {
        val config = HarnessConfig(playerNamePrefix = "twelve_chars")

        assertThrows<IllegalArgumentException> { config.validated() }
    }

    @Test
    fun `validated rejects an empty message buffer`() {
        val config = HarnessConfig(messageBufferSize = 0)

        assertThrows<IllegalArgumentException> { config.validated() }
    }

    @Test
    fun `validated rejects a non-positive request timeout`() {
        val config = HarnessConfig(requestTimeoutMillis = 0)

        assertThrows<IllegalArgumentException> { config.validated() }
    }

    @Test
    fun `load falls back to defaults when the file is missing`(@TempDir directory: Path) {
        val config = HarnessConfig.load(directory.resolve("missing.jsonc"), emptyMap())

        assertEquals(HarnessConfig(), config)
    }

    @Test
    fun `load applies the environment on top of the file`(@TempDir directory: Path) {
        val file = Files.writeString(directory.resolve("harness.jsonc"), """{ "enabled": false, "port": 50001 }""")

        val config = HarnessConfig.load(file, mapOf(HarnessConfig.ENABLED_VARIABLE to "true"))

        assertEquals(HarnessConfig(enabled = true, port = 50001), config)
    }

    @Test
    fun `load rejects invalid settings from the file`(@TempDir directory: Path) {
        val file = Files.writeString(directory.resolve("harness.jsonc"), """{ "bind_address": "192.168.1.10" }""")

        assertThrows<IllegalArgumentException> { HarnessConfig.load(file, emptyMap()) }
    }

    @Test
    fun `shipped config file is valid and keeps the harness off`() {
        val config = HarnessConfig.load(HarnessConfig.PATH, emptyMap())

        assertEquals(HarnessConfig(), config)
    }
}
