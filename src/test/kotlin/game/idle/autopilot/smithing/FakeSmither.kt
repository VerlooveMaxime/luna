package game.idle.autopilot.smithing

import game.idle.autopilot.Candidate
import game.idle.location.Tile
import io.luna.game.model.Position

/** Records each step as readable text, for example `choose 10`; [anchor] is the step's area anchor. */
class FakeSmither(var view: SmithingView, private val anchor: Tile = Tile(3200, 3200)) : Smither {

    var busy = false
    var made = 0
    val steps = mutableListOf<String>()

    override fun isBusy(): Boolean = busy

    override fun look(): SmithingView = view

    override fun useOn(anvil: Candidate, slot: Int) {
        steps += "use $slot on ${anvil.position.x},${anvil.position.y}"
    }

    override fun choose(times: Int) {
        steps += "choose $times"
    }

    override fun walkTo(anvil: Candidate) {
        steps += "walk to ${anvil.position.x},${anvil.position.y}"
    }

    override fun anchor(): Tile = anchor

    override fun made(): Int = made

    override fun interrupt() {
        steps += "interrupt"
    }
}

fun anvil(x: Int, y: Int, distance: Int, usableFromHere: Boolean = false) =
    Candidate(2783, Position(x, y), distance, usableFromHere, Position(x - 1, y))

fun smithingView(
    anvils: List<Candidate>,
    barSlot: Int? = 0,
    windowOpen: Boolean = false,
    atLocation: Boolean = true,
) = SmithingView(barSlot, windowOpen, atLocation, anvils)
