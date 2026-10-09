package game.idle.autopilot.firemaking

/** Records each step as text, for example `light 3`; [aside] is what a step aside answers. */
class FakeLighter(var view: LightView) : Lighter {

    var busy = false
    var aside = true
    val steps = mutableListOf<String>()

    override fun isBusy(): Boolean = busy

    override fun look(): LightView = view

    override fun light(slot: Int) {
        steps += "light $slot"
    }

    override fun stepAside(): Boolean {
        steps += "step aside"
        return aside
    }
}
