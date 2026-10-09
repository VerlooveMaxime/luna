package game.idle.autopilot.making

/** Records each step as text, for example `use 0 on 1` or `choose 0 x28`. */
class FakeMaker(var view: MakeView) : Maker {

    var busy = false
    var products = 0
    val steps = mutableListOf<String>()

    override fun isBusy(): Boolean = busy

    override fun look(): MakeView = view

    override fun use(useSlot: Int, onSlot: Int) {
        steps += "use $useSlot on $onSlot"
    }

    override fun choose(index: Int, times: Int) {
        steps += "choose $index x$times"
    }

    override fun products(): Int = products

    override fun stop() {
        steps += "stop"
    }
}
