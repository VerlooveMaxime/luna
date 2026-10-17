package game.idle.autopilot

import game.idle.IdleState
import game.idle.flow.Clock
import game.idle.flow.StepWords
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
 * the data file); a fresh run starts on the [clock]'s time (S12). However a run ends, its driver is told so it lets go
 * of what it holds for the player (S13 part 3). Game thread only.
 */
class Autopilot<P : AutopilotPlayer>(
    private val scheduler: TickScheduler,
    private val clock: Clock,
    private val newDriver: (P) -> AutopilotDriver?,
) {

    private val running = mutableMapOf<String, Run>()

    /** A player's running autopilot: its tick and the driver it drives. */
    private class Run(private val tick: ScheduledTick, private val driver: AutopilotDriver) {

        fun end() {
            tick.cancel()
            driver.end()
        }
    }

    /**
     * Reads the state on every login, not only when it says running: Luna's save drops a persistent attribute that
     * was loaded but never read during the session.
     */
    fun onLogin(player: P) {
        if (player.idleState.running && !start(player)) {
            player.tell(StepWords.CANNOT_RESUME)
        }
    }

    fun onLogout(player: P) {
        running.remove(player.username)?.end()
    }

    fun isRunning(player: P): Boolean = player.username in running

    /**
     * Runs the saved flow from its saved step, replacing whatever ran before; false when it does not resolve. A flow
     * started from its first step takes the player's tile as its run tile and the time as its start, a resumed one
     * keeps its own; the driver is built from the run as started, so it reads that start.
     */
    fun start(player: P): Boolean {
        stop(player)
        player.idleState = player.idleState.started(player.tile, clock.now())
        val driver = newDriver(player)
        if (driver == null) {
            player.idleState = player.idleState.stopped()
            return false
        }
        running[player.username] = Run(scheduler.everyTick { tick(player, driver) }, driver)
        return true
    }

    /**
     * A step that ends the flow (a reflex's Stop the flow, say) stops it and tells the player why. A walk it started
     * goes on: a reflex stops the flow once nothing attacks the player, maybe before their run is over. Otherwise the
     * current step's block and wait go into the state, only when they change, since every state change refreshes the
     * UI; a step that starts being blocked says so in the chat box, pointing at its slot for the reason, while a wait
     * for the world is shown and never told (Maxime, 2026-10-11).
     */
    private fun tick(player: P, driver: AutopilotDriver) {
        driver.tick()
        val reason = driver.stopReason()
        if (reason == null) {
            noteHeld(player, driver.blocked(), driver.waiting(), driver.underWay())
            return
        }
        halt(player)
        player.tell(reason)
    }

    private fun noteHeld(player: P, blocked: String?, waiting: String?, underWay: String?) {
        val state = player.idleState
        if (state.blocked == blocked && state.waiting == waiting && state.underWay == underWay) return
        if (state.blocked == null && blocked != null) player.tell(StepWords.cannotWork(state.stepIndex + 1))
        player.idleState = state.copy(blocked = blocked, waiting = waiting, underWay = underWay)
    }

    /** Stops the flow and the walk it was taking the player on (to a bank, say), which would carry them far off. */
    fun stop(player: P) {
        if (halt(player)) {
            player.endWalk()
        }
    }

    /** Stops the flow; true when one was running. */
    private fun halt(player: P): Boolean {
        val run = running.remove(player.username)
        run?.end()
        player.idleState = player.idleState.stopped()
        return run != null
    }
}
