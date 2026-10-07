package game.idle.autopilot

import game.idle.IdleState
import game.idle.location.Tile

class FakeAutopilotPlayer(override val username: String, state: IdleState = IdleState()) : AutopilotPlayer {

    val told = mutableListOf<String>()
    var walksEnded = 0
        private set
    var stateReads = 0
        private set

    override var idleState: IdleState = state
        get() {
            stateReads++
            return field
        }

    override var tile: Tile = Tile(3200, 3200)

    override fun tell(message: String) {
        told += message
    }

    override fun endWalk() {
        walksEnded++
    }
}
