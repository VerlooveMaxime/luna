package game.idle.autopilot

import io.luna.util.GsonUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Settings from `data/idle/autopilot.jsonc`. Every field has a default so Gson can use the no-arg constructor. */
data class AutopilotConfig(
    val decisionDelayTicks: Int = 2,
) {

    fun validated(): AutopilotConfig {
        require(decisionDelayTicks > 0) { "decision_delay_ticks must be positive, got $decisionDelayTicks" }
        return this
    }

    companion object {
        val PATH: Path = Paths.get("data", "idle", "autopilot.jsonc")

        fun parse(jsonc: String): AutopilotConfig = GsonUtils.GSON.fromJson(jsonc, AutopilotConfig::class.java)

        /** Reads [path], falling back to defaults when the file does not exist. */
        fun load(path: Path): AutopilotConfig =
            (if (Files.exists(path)) parse(Files.readString(path)) else AutopilotConfig()).validated()
    }
}
