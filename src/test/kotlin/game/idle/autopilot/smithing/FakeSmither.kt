package game.idle.autopilot.smithing

import game.idle.autopilot.PlaceCandidate
import io.luna.game.model.Position

/** Records each step as readable text, for example `choose 10`. */
class FakeSmither(var view: SmithingView) : Smither {

    var busy = false
    var made = 0
    val steps = mutableListOf<String>()

    override fun isBusy(): Boolean = busy

    override fun look(): SmithingView = view

    override fun useOn(anvil: PlaceCandidate, slot: Int) {
        steps += "use $slot on ${anvil.position.x},${anvil.position.y}"
    }

    override fun choose(times: Int) {
        steps += "choose $times"
    }

    override fun walkTo(anvil: PlaceCandidate) {
        steps += "walk to ${anvil.position.x},${anvil.position.y}"
    }

    override fun walkToLocation() {
        steps += "walk to location"
    }

    override fun made(): Int = made

    override fun stop() {
        steps += "stop"
    }

    override fun tell(message: String) {
        steps += "tell $message"
    }
}

fun anvil(x: Int, y: Int, distance: Int, usableFromHere: Boolean = false) =
    PlaceCandidate(2783, Position(x, y), distance, usableFromHere, Position(x - 1, y))

fun smithingView(
    anvils: List<PlaceCandidate>,
    barSlot: Int? = 0,
    hasHammer: Boolean = true,
    smithingLevel: Int = 1,
    windowOpen: Boolean = false,
    atLocation: Boolean = true,
) = SmithingView(barSlot, hasHammer, smithingLevel, windowOpen, atLocation, anvils)
