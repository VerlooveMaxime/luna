package game.idle.autopilot.firemaking

/** Records each step as text, for example `light 3`; [aside] is what a move to a free tile answers. */
class FakeLighter(var view: LightView) : Lighter {

    var busy = false
    var aside = true
    val steps = mutableListOf<String>()

    override fun isBusy(): Boolean = busy

    override fun look(): LightView = view

    override fun light(slot: Int) {
        steps += "light $slot"
    }

    override fun moveToFreeTile(): Boolean {
        steps += "move to a free tile"
        return aside
    }
}
