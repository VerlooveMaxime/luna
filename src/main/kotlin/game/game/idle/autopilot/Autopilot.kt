package game.idle.autopilot

import game.idle.IdleState
import game.idle.location.Tile

/** The parts of a player the autopilot switch needs. [LunaAutopilotPlayer] is the in-game one. */
interface AutopilotPlayer {

    val username: String

    var idleState: IdleState

    /** Where the player stands. */
    val tile: Tile

    fun tell(message: String)

    /** Ends the walk the autopilot is taking the player on, if any; a walk the player clicked goes on. */
    fun endWalk()
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
 * [newDriver] builds the driver for the saved flow, or gives null when it no longer resolves (a bank removed from
 * the data file). Game thread only.
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
            player.tell("Autopilot: could not resume your flow. Check it in the flow builder.")
        }
    }

    fun onLogout(player: P) {
        running.remove(player.username)?.cancel()
    }

    fun isRunning(player: P): Boolean = player.username in running

    /**
     * Runs the saved flow from its saved step, replacing whatever ran before; false when it does not resolve. A flow
     * started from its first step takes the player's tile as its run tile, a resumed one keeps its own.
     */
    fun start(player: P): Boolean {
        val driver = newDriver(player)
        stop(player)
        if (driver == null) {
            return false
        }
        player.idleState = player.idleState.started(player.tile)
        running[player.username] = scheduler.everyTick { tick(player, driver) }
        return true
    }

    /**
     * A step that ends the flow (out of food, say) stops it and tells the player why. A walk it started goes on: the
     * fight step stops once it has run from what attacked the player, maybe before the run is over. Otherwise the
     * current step's block goes into the state, only when it changes, since every state change refreshes the UI; a
     * step that starts being blocked says so in the chat box, pointing at its slot for the reason.
     */
    private fun tick(player: P, driver: AutopilotDriver) {
        driver.tick()
        val reason = driver.stopReason()
        if (reason == null) {
            noteBlocked(player, driver.blocked())
            return
        }
        halt(player)
        player.tell(reason)
    }

    private fun noteBlocked(player: P, blocked: String?) {
        val state = player.idleState
        if (state.blocked == blocked) return
        if (state.blocked == null) player.tell(cannotWork(state.stepIndex + 1))
        player.idleState = state.copy(blocked = blocked)
    }

    /** Stops the flow and the walk it was taking the player on (to a bank, say), which would carry them far off. */
    fun stop(player: P) {
        if (halt(player)) {
            player.endWalk()
        }
    }

    /** Stops the flow; true when one was running. */
    private fun halt(player: P): Boolean {
        val tick = running.remove(player.username)
        tick?.cancel()
        player.idleState = player.idleState.stopped()
        return tick != null
    }

    companion object {
        /**
         * The chat line for step [number] when it cannot work, at Run or while running: the reason is on the step's
         * slot, and in full it is often too long for a chat line (Maxime, 2026-10-09).
         */
        fun cannotWork(number: Int): String = "Autopilot: step $number cannot work yet. See its slot warning in the builder."
    }
}
