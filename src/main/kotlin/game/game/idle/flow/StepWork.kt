package game.idle.flow

/**
 * What a step's Amount counts: nothing, a reading that rises as the work goes (logs, ores, catches, hitpoints), or one
 * that falls (the logs a light step uses up).
 */
sealed interface Meter {

    data object None : Meter

    class Rises(val reading: () -> Int) : Meter

    class Falls(val reading: () -> Int) : Meter
}

/**
 * A kind's own part of a step's turn: it looks, decides and clicks, and says what its decision came to as a
 * [StepOutcome]; [StepTurn] applies the rules every kind shares around it.
 */
interface StepWork {

    val meter: Meter

    /** The adapter's busy rule, kept per kind with its reason (a fisher out of bait is never busy). */
    fun isBusy(): Boolean

    /** Whether the unit of work in progress still has to settle before the step may end: a fire catching, a kill dying. */
    fun finishing(): Boolean

    /** Breaks off the Luna action in progress, as reaching the Amount does. */
    fun interrupt()

    /** One decision; [left] is what the Amount still asks for, null without one. */
    fun act(left: Int?): StepOutcome

    /** A line the last decision has for the chat box, once: a door a walk up could not get through. */
    fun said(): Said? = null

    /**
     * Whether the work took the player somewhere, when that is the step's own work (the walk step's trip), which then
     * counts as work done: a lap of walks that move the player is no idle lap (Maxime, 2026-10-20).
     */
    fun travelled(): Boolean = false
}
