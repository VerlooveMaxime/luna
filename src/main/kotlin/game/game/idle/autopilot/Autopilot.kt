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
 * Starts and stops each player's autopilot from the flow saved in their [IdleState]; at most one runs per player.
 * [newDriver] builds the driver for the saved flow, or gives null when it no longer resolves (a location removed
 * from the data file). Game thread only.
 */
class Autopilot<P : AutopilotPlayer>(
    private val scheduler: TickScheduler,
    private val newDriver: (P) -> AutopilotDriver?,
) {

    private val running = mutableMapOf<String, ScheduledTick>()

    /**
     * Reads the state on every login, not only when it says running: Luna's save drops a persistent attribute that
     * was loaded but never read during the session.
     */
    fun onLogin(player: P) {
        if (player.idleState.running && !start(player)) {
            player.tell("Autopilot: could not resume your flow. Check it with ::flow list.")
        }
    }

    fun onLogout(player: P) {
        running.remove(player.username)?.cancel()
    }

    fun isRunning(player: P): Boolean = player.username in running

    /** Runs the saved flow from its saved step, replacing whatever ran before; false when it does not resolve. */
    fun start(player: P): Boolean {
        val driver = newDriver(player)
        stop(player)
        if (driver == null) {
            return false
        }
        player.idleState = player.idleState.started()
        running[player.username] = scheduler.everyTick(driver::tick)
        return true
    }

    fun stop(player: P) {
        running.remove(player.username)?.cancel()
        player.idleState = player.idleState.stopped()
    }
}
