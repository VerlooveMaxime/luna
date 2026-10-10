package game.idle.autopilot

import game.idle.ui.FlowWidgets
import io.luna.util.GsonUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Settings from `data/idle/autopilot.jsonc`. Every field has a default so Gson can use the no-arg constructor. */
data class AutopilotConfig(
    val decisionDelayTicks: Int = 2,
    val stepSlots: Int = 4,
    val savedFlowSlots: Int = 2,
    val reflexSlots: Int = 4,
) {

    fun validated(): AutopilotConfig {
        require(decisionDelayTicks > 0) { "decision_delay_ticks must be positive, got $decisionDelayTicks" }
        require(stepSlots > 0) { "step_slots must be positive, got $stepSlots" }
        require(reflexSlots > 0) { "reflex_slots must be positive, got $reflexSlots" }
        require(savedFlowSlots in 1..FlowWidgets.MOST_SAVED_SLOTS) {
            "saved_flow_slots must be 1 to ${FlowWidgets.MOST_SAVED_SLOTS}, got $savedFlowSlots"
        }
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
