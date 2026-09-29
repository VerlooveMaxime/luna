package game.idle.autopilot

import game.idle.IdleState

class FakeAutopilotPlayer(override val username: String, state: IdleState = IdleState()) : AutopilotPlayer {

    val told = mutableListOf<String>()
    var stateReads = 0
        private set

    override var idleState: IdleState = state
        get() {
            stateReads++
            return field
        }

    override fun tell(message: String) {
        told += message
    }
}
