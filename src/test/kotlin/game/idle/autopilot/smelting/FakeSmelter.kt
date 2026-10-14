package game.idle.autopilot.smelting

import game.idle.autopilot.Candidate
import game.idle.location.Tile
import io.luna.game.model.Position

/** Records each step as readable text, for example `smelt 3 on 3200,3200`; [anchor] is the step's area anchor. */
class FakeSmelter(var view: SmeltingView, private val anchor: Tile = Tile(3200, 3200)) : Smelter {

    var busy = false
    var bars = 0
    val steps = mutableListOf<String>()

    override fun isBusy(): Boolean = busy

    override fun look(): SmeltingView = view

    override fun smelt(furnace: Candidate, slot: Int) {
        steps += "smelt $slot on ${furnace.position.x},${furnace.position.y}"
    }

    override fun walkTo(furnace: Candidate) {
        steps += "walk to ${furnace.position.x},${furnace.position.y}"
    }

    override fun anchor(): Tile = anchor

    override fun bars(): Int = bars

    override fun interrupt() {
        steps += "interrupt"
    }
}

fun furnace(x: Int, y: Int, distance: Int, usableFromHere: Boolean = false) =
    Candidate(2781, Position(x, y), distance, usableFromHere, Position(x - 1, y))

fun smeltingView(furnaces: List<Candidate>, oreSlot: Int? = 0, atLocation: Boolean = true) =
    SmeltingView(oreSlot, atLocation, furnaces)
