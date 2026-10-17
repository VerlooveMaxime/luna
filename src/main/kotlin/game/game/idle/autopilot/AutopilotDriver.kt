package game.idle.autopilot

import kotlin.random.Random

/** Something the autopilot trains: it knows whether the player is still busy with it and takes the next step. */
interface AutopilotActivity {

    fun isBusy(): Boolean

    fun act()

    /** Why the whole autopilot must stop now, or null to carry on. */
    fun stopReason(): String?

    /** Why the activity cannot go on for now, as the fragment the overlay and the slot show, or null while it can. */
    fun blocked(): String?

    /** What the activity waits for the world to supply, as the fragment the overlay and the slot show in yellow, or null. */
    fun waiting(): String?

    /** What the activity does on its way (opening a door), as the fragment the overlay and the slot show in white, or null. */
    fun underWay(): String?

    /** The autopilot is done with the activity, stopped for whatever reason: it lets go of what it holds for the player (the prayers it lit, S13 part 3). */
    fun end()
}

/** How many idle ticks the player stands before each decision: the pause a real player takes between clicks. */
fun interface DecisionDelay {

    fun nextTicks(): Int

    companion object {
        /** A pause drawn afresh before each decision from [ticks], each as likely (Maxime, 2026-10-17: a fixed one felt unnatural). */
        fun between(ticks: IntRange, random: Random = Random.Default): DecisionDelay = DecisionDelay { ticks.random(random) }
    }
}

/**
 * Lets [activity] take a step once the player has stood idle for as many ticks in a row as [delay] draws, drawn again
 * after each step. [tick] must be called once per game tick.
 */
class AutopilotDriver(private val activity: AutopilotActivity, private val delay: DecisionDelay) {

    private var idleTicks = 0

    private var wait = drawn()

    private fun drawn(): Int = delay.nextTicks().also { require(it > 0) { "a decision delay must be positive, got $it" } }

    fun stopReason(): String? = activity.stopReason()

    fun blocked(): String? = activity.blocked()

    fun waiting(): String? = activity.waiting()

    fun underWay(): String? = activity.underWay()

    fun end() = activity.end()

    fun tick() {
        if (activity.isBusy()) {
            idleTicks = 0
            return
        }
        idleTicks++
        if (idleTicks >= wait) {
            idleTicks = 0
            wait = drawn()
            activity.act()
        }
    }
}
