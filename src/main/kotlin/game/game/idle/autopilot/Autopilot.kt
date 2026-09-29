package game.idle.autopilot

import game.idle.IdleState

/** The parts of a player the autopilot switch needs. [LunaAutopilotPlayer] is the in-game one. */
interface AutopilotPlayer {

    val username: String

    var idleState: IdleState

    fun tell(message: String)
}

/** Runs an action every game tick until the returned handle is cancelled. */
fun interface TickScheduler {

    fun everyTick(action: () -> Unit): ScheduledTick
}

fun interface ScheduledTick {

    fun cancel()
}

/**
 * Starts and stops each player's autopilot from the switch saved in their [IdleState]; at most one runs per player.
 * Game thread only.
 */
class Autopilot<P : AutopilotPlayer>(
    private val scheduler: TickScheduler,
    private val newDriver: (P) -> AutopilotDriver,
) {

    private val running = mutableMapOf<String, ScheduledTick>()

    /**
     * Reads the switch on every login, not only when it is on: Luna's save drops a persistent attribute that was
     * loaded but never read during the session.
     */
    fun onLogin(player: P) {
        if (player.idleState.autopilotEnabled) {
            start(player)
        }
    }

    fun onLogout(player: P) {
        stop(player.username)
    }

    fun toggle(player: P) {
        val enabled = !player.idleState.autopilotEnabled
        player.idleState = player.idleState.withAutopilot(enabled)
        if (enabled) {
            start(player)
            player.tell("Autopilot enabled.")
        } else {
            stop(player.username)
            player.tell("Autopilot disabled.")
        }
    }

    private fun start(player: P) {
        if (player.username !in running) {
            running[player.username] = scheduler.everyTick(newDriver(player)::tick)
        }
    }

    private fun stop(username: String) {
        running.remove(username)?.cancel()
    }
}
