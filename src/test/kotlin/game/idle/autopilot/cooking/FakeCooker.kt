package game.idle.autopilot.cooking

import game.idle.autopilot.PlaceCandidate

/** Records each step as text, for example `use 2 on 3100,3095`. */
class FakeCooker(var view: CookingView) : Cooker {

    var busy = false
    var raw = 0
    val steps = mutableListOf<String>()

    override fun isBusy(): Boolean = busy

    override fun look(): CookingView = view

    override fun useOn(place: PlaceCandidate, slot: Int) {
        steps += "use $slot on ${place.position.x},${place.position.y}"
    }

    override fun cookAll() {
        steps += "cook all"
    }

    override fun walkTo(place: PlaceCandidate) {
        steps += "walk to ${place.position.x},${place.position.y}"
    }

    override fun walkToLocation() {
        steps += "walk to location"
    }

    override fun raw(): Int = raw

    override fun stop() {
        steps += "stop"
    }
}
