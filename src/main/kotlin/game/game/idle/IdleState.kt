package game.idle

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import io.luna.game.model.mob.Player

/** What the autopilot was told to do, as saved: a location id and tree names, resolved again on login. */
data class AutopilotJob(val locationId: String, val trees: List<String>)

/** Per-player IdleRS progress. Persisted as one player attribute, so every field must stay Gson-friendly. */
data class IdleState(
    val job: AutopilotJob? = null,
    val stage: Int = 0,
    val resets: Int = 0,
) {
    val autopilotEnabled: Boolean get() = job != null

    fun withJob(job: AutopilotJob): IdleState = copy(job = job)

    fun stopped(): IdleState = copy(job = null)
}

var Player.idleState by Attr.obj { IdleState() }.persist("idle_state")
