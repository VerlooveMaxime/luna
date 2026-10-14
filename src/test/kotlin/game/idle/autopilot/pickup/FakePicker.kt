package game.idle.autopilot.pickup

import game.idle.autopilot.Candidate

/** Records each step as text, for example `take 3200,3201`. */
class FakePicker(var view: PickUpView) : Picker {

    var busy = false
    var pickedUp = 0
    val steps = mutableListOf<String>()

    override fun isBusy(): Boolean = busy

    override fun look(): PickUpView = view

    override fun take(item: Candidate) {
        steps += "take ${item.position.x},${item.position.y}"
    }

    override fun walkTo(item: Candidate) {
        steps += "walk to ${item.position.x},${item.position.y}"
    }

    override fun walkToLocation() {
        steps += "walk to location"
    }

    override fun pickedUp(): Int = pickedUp

    override fun interrupt() {
        steps += "interrupt"
    }
}
