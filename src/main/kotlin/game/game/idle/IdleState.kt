package game.idle

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import io.luna.game.model.mob.Player

/** Per-player IdleRS progress. Persisted as one player attribute, so every field must stay Gson-friendly. */
data class IdleState(
    val autopilotEnabled: Boolean = false,
    val stage: Int = 0,
    val resets: Int = 0,
) {
    fun withAutopilot(enabled: Boolean): IdleState = copy(autopilotEnabled = enabled)
}

var Player.idleState by Attr.obj { IdleState() }.persist("idle_state")
