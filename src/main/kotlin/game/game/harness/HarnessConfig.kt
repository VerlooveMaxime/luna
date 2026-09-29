package game.harness

import io.luna.util.GsonUtils
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration

/** Settings from `data/idle/harness.jsonc`. Every field has a default so Gson can use the no-arg constructor. */
data class HarnessConfig(
    val enabled: Boolean = false,
    val bindAddress: String = "127.0.0.1",
    val port: Int = 43595,
    val playerNamePrefix: String = "agent_",
    val playerPassword: String = "harness",
    val messageBufferSize: Int = 500,
    val requestTimeoutMillis: Long = 10_000,
) {

    val requestTimeout: Duration
        get() = Duration.ofMillis(requestTimeoutMillis)

    /** Applies [ENABLED_VARIABLE] on top of the file value. */
    fun withEnvironment(environment: Map<String, String>): HarnessConfig {
        val override = environment[ENABLED_VARIABLE] ?: return this
        return copy(enabled = parseSwitch(override))
    }

    /** Rejects settings that would expose the API beyond this machine or cannot work. */
    fun validated(): HarnessConfig {
        require(InetAddress.getByName(bindAddress).isLoopbackAddress) {
            "bind_address must be a loopback address, got '$bindAddress'"
        }
        require(port in 1..65535) { "port must be between 1 and 65535, got $port" }
        require(playerNamePrefix.length < MAX_USERNAME_LENGTH) {
            "player_name_prefix must leave room for a name within $MAX_USERNAME_LENGTH characters"
        }
        require(messageBufferSize > 0) { "message_buffer_size must be positive, got $messageBufferSize" }
        require(requestTimeoutMillis > 0) { "request_timeout_millis must be positive, got $requestTimeoutMillis" }
        return this
    }

    companion object {
        val PATH: Path = Paths.get("data", "idle", "harness.jsonc")
        const val ENABLED_VARIABLE = "IDLERS_HARNESS"
        const val MAX_USERNAME_LENGTH = 12

        fun parse(jsonc: String): HarnessConfig = GsonUtils.GSON.fromJson(jsonc, HarnessConfig::class.java)

        /** Reads [path], falling back to defaults (harness off) when the file does not exist. */
        fun load(path: Path, environment: Map<String, String>): HarnessConfig {
            val fromFile = if (Files.exists(path)) parse(Files.readString(path)) else HarnessConfig()
            return fromFile.withEnvironment(environment).validated()
        }

        private fun parseSwitch(value: String): Boolean =
            when (value.trim().lowercase()) {
                "true", "1" -> true
                "false", "0" -> false
                else -> throw IllegalArgumentException("$ENABLED_VARIABLE must be true, false, 1 or 0, got '$value'")
            }
    }
}
