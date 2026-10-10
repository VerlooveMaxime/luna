package game.idle.autopilot.pickup

/** Records each step as text, for example `take 3200,3201`, and what it was told. */
class FakePicker(var view: PickUpView) : Picker {

    var busy = false
    var pickedUp = 0
    val steps = mutableListOf<String>()
    val told = mutableListOf<String>()

    override fun isBusy(): Boolean = busy

    override fun look(): PickUpView = view

    override fun take(item: GroundLoot) {
        steps += "take ${item.position.x},${item.position.y}"
    }

    override fun walkTo(item: GroundLoot) {
        steps += "walk to ${item.position.x},${item.position.y}"
    }

    override fun walkToLocation() {
        steps += "walk to location"
    }

    override fun pickedUp(): Int = pickedUp

    override fun tell(message: String) {
        told += message
    }
}
