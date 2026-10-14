package game.idle.autopilot.woodcutting

import game.idle.autopilot.Candidate
import game.idle.location.Tile

/** Records each step as readable text, for example `walk to 3170,3454`; [anchor] is the step's area anchor. */
class FakeWoodcutter(var view: WoodcuttingView, private val anchor: Tile = Tile(3200, 3200)) : Woodcutter {

    var busy = false
    var logs = 0
    val steps = mutableListOf<String>()

    override fun isBusy(): Boolean = busy

    override fun look(): WoodcuttingView = view

    override fun chop(tree: Candidate) {
        steps += "chop ${tree.position.x},${tree.position.y}"
    }

    override fun walkTo(tree: Candidate) {
        steps += "walk to ${tree.position.x},${tree.position.y}"
    }

    override fun anchor(): Tile = anchor

    override fun logs(): Int = logs

    override fun interrupt() {
        steps += "interrupt"
    }
}
