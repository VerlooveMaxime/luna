package game.idle.autopilot.fishing

import game.idle.location.Tile

/** Records each step as text, for example `fish 3100,3092`; [anchor] is the step's area anchor. */
class FakeFisher(var view: FishingView, private val anchor: Tile = Tile(3200, 3200)) : Fisher {

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

    override fun anchor(): Tile = anchor

    override fun catches(): Int = catches

    override fun interrupt() {
        steps += "interrupt"
    }
}
