package game.idle.autopilot.smelting

import game.idle.autopilot.PlaceCandidate
import io.luna.game.model.Position

/** Records each step as readable text, for example `smelt 3,3200,3200`. */
class FakeSmelter(var view: SmeltingView) : Smelter {

    var busy = false
    var bars = 0
    val steps = mutableListOf<String>()

    override fun isBusy(): Boolean = busy

    override fun look(): SmeltingView = view

    override fun smelt(furnace: PlaceCandidate, slot: Int) {
        steps += "smelt $slot,${furnace.position.x},${furnace.position.y}"
    }

    override fun walkTo(furnace: PlaceCandidate) {
        steps += "walk to ${furnace.position.x},${furnace.position.y}"
    }

    override fun walkToLocation() {
        steps += "walk to location"
    }

    override fun bars(): Int = bars

    override fun stop() {
        steps += "stop"
    }
}

fun furnace(x: Int, y: Int, distance: Int, usableFromHere: Boolean = false) =
    PlaceCandidate(2781, Position(x, y), distance, usableFromHere, Position(x - 1, y))

fun smeltingView(furnaces: List<PlaceCandidate>, oreSlot: Int? = 0, smithingLevel: Int = 1, atLocation: Boolean = true) =
    SmeltingView(oreSlot, smithingLevel, atLocation, furnaces)
