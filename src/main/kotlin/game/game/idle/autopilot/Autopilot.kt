package game.idle.autopilot

import game.idle.AutopilotJob
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
 * Starts and stops each player's autopilot from the job saved in their [IdleState]; at most one runs per player.
 * [newDriver] builds the driver for a job, or gives null when the job no longer resolves (a location removed from
 * the data file). Game thread only.
 */
class Autopilot<P : AutopilotPlayer>(
    private val scheduler: TickScheduler,
    private val newDriver: (P, AutopilotJob) -> AutopilotDriver?,
) {

    private val running = mutableMapOf<String, ScheduledTick>()

    /**
     * Reads the state on every login, not only when a job is saved: Luna's save drops a persistent attribute that
     * was loaded but never read during the session.
     */
    fun onLogin(player: P) {
        val job = player.idleState.job ?: return
        if (!start(player, job)) {
            player.tell("Autopilot: could not resume at '${job.locationId}'. Use ::idle to start again.")
        }
    }

    fun onLogout(player: P) {
        running.remove(player.username)?.cancel()
    }

    fun isRunning(player: P): Boolean = player.username in running

    /** Replaces whatever the player was doing with [job] and saves it; false when the job does not resolve. */
    fun start(player: P, job: AutopilotJob): Boolean {
        val driver = newDriver(player, job)
        stop(player)
        if (driver == null) {
            return false
        }
        player.idleState = player.idleState.withJob(job)
        running[player.username] = scheduler.everyTick(driver::tick)
        return true
    }

    fun stop(player: P) {
        running.remove(player.username)?.cancel()
        player.idleState = player.idleState.stopped()
    }
}
