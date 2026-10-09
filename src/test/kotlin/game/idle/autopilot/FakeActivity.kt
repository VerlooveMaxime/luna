package game.idle.autopilot

class FakeActivity : AutopilotActivity {

    var busy = false
    var stop: String? = null
    var blocked: String? = null
    var steps = 0
        private set

    override fun isBusy(): Boolean = busy

    override fun stopReason(): String? = stop

    override fun blocked(): String? = blocked

    override fun act() {
        steps++
    }
}
