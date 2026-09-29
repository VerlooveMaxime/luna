package game.idle.autopilot

class FakeActivity : AutopilotActivity {

    var busy = false
    var steps = 0
        private set

    override fun isBusy(): Boolean = busy

    override fun act() {
        steps++
    }
}
