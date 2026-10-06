package game.idle.autopilot.fishing

/** Records each step as text, for example `fish 3100,3092`. */
class FakeFisher(var view: FishingView) : Fisher {

    var busy = false
    var catches = 0
    val steps = mutableListOf<String>()

    override fun isBusy(): Boolean = busy

    override fun look(): FishingView = view

    override fun fish(spot: SpotCandidate) {
        steps += "fish ${spot.position.x},${spot.position.y}"
    }

    override fun walkTo(spot: SpotCandidate) {
        steps += "walk to ${spot.position.x},${spot.position.y}"
    }

    override fun walkToLocation() {
        steps += "walk to location"
    }

    override fun catches(): Int = catches

    override fun stop() {
        steps += "stop"
    }

    override fun tell(message: String) {
        steps += "tell $message"
    }
}
