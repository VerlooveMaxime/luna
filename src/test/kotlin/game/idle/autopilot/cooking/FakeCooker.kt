package game.idle.autopilot.cooking

import game.idle.autopilot.PlaceCandidate
import game.idle.location.Tile
import io.luna.game.model.Position

/** Records each step as text, for example `use 2 on 3100,3095`; [anchor] is the step's area anchor. */
class FakeCooker(var view: CookingView, private val anchor: Tile = Tile(3200, 3200)) : Cooker {

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

    override fun anchor(): Tile = anchor

    override fun raw(): Int = raw

    override fun interrupt() {
        steps += "interrupt"
    }
}

fun cookingPlace(x: Int, y: Int, distance: Int, usableFromHere: Boolean = false) =
    PlaceCandidate(2732, Position(x, y), distance, usableFromHere, Position(x - 1, y))

fun cookingView(places: List<PlaceCandidate>, rawSlot: Int? = 2, windowOpen: Boolean = false, atLocation: Boolean = true) =
    CookingView(rawSlot, windowOpen, atLocation, places)
