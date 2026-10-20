package game.idle.autopilot.pickup

import game.idle.autopilot.Candidate
import game.idle.location.Tile

/** Records each step as text, for example `take 3200,3201`; [anchor] is the step's area anchor. */
class FakePicker(var view: PickUpView, private val anchor: Tile = Tile(3200, 3200)) : Picker {

    var busy = false
    var pickedUp = 0
    val steps = mutableListOf<String>()

    override fun isBusy(): Boolean = busy

    override fun look(): PickUpView = view

    override fun take(item: Candidate) {
        steps += "take ${item.position.x},${item.position.y}"
    }

    override fun anchor(): Tile = anchor

    override fun pickedUp(): Int = pickedUp

    override fun interrupt() {
        steps += "interrupt"
    }
}
