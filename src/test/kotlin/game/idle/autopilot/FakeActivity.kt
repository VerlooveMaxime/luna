package game.idle.autopilot

class FakeActivity : AutopilotActivity {

    var busy = false
    var stop: String? = null
    var blocked: String? = null
    var waiting: String? = null
    var steps = 0
        private set
    var ended = 0
        private set

    /** What ending does besides counting, for a test that watches the order of what follows. */
    var onEnd: () -> Unit = {}

    override fun isBusy(): Boolean = busy

    override fun stopReason(): String? = stop

    override fun blocked(): String? = blocked

    override fun waiting(): String? = waiting

    override fun act() {
        steps++
    }

    override fun end() {
        ended++
        onEnd()
    }
}
