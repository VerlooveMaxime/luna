package game.idle.autopilot

/** Something the autopilot trains: it knows whether the player is still busy with it and takes the next step. */
interface AutopilotActivity {

    fun isBusy(): Boolean

    fun act()
}

/**
 * Lets [activity] take a step once the player has stood idle for [decisionDelayTicks] ticks in a row: the pause a
 * real player takes between clicks. [tick] must be called once per game tick.
 */
class AutopilotDriver(private val activity: AutopilotActivity, private val decisionDelayTicks: Int) {

    private var idleTicks = 0

    init {
        require(decisionDelayTicks > 0) { "decisionDelayTicks must be positive, got $decisionDelayTicks" }
    }

    fun tick() {
        if (activity.isBusy()) {
            idleTicks = 0
            return
        }
        idleTicks++
        if (idleTicks >= decisionDelayTicks) {
            idleTicks = 0
            activity.act()
        }
    }
}
