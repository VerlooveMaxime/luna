package game.idle

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import io.luna.game.model.mob.Player

/**
 * Per-player IdleRS progress. Persisted as one player attribute, so every field must stay Gson-friendly: the flow
 * is kept as the lines the player typed and parsed again when it runs.
 */
data class IdleState(
    val flow: List<String> = emptyList(),
    val stepIndex: Int = 0,
    val running: Boolean = false,
    val stage: Int = 0,
    val resets: Int = 0,
) {
    fun withFlow(flow: List<String>): IdleState = copy(flow = flow, stepIndex = 0, running = false)

    fun atStep(index: Int): IdleState = copy(stepIndex = index)

    fun started(): IdleState = copy(running = true)

    fun stopped(): IdleState = copy(running = false)
}

var Player.idleState by Attr.obj { IdleState() }.persist("idle_state")
