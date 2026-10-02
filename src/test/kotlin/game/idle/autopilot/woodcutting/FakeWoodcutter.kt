package game.idle.autopilot.woodcutting

/** Records each step as readable text, for example `walk to 3170,3454`. */
class FakeWoodcutter(var view: WoodcuttingView) : Woodcutter {

    var busy = false
    val steps = mutableListOf<String>()

    override fun isBusy(): Boolean = busy

    override fun look(): WoodcuttingView = view

    override fun chop(tree: TreeCandidate) {
        steps += "chop ${tree.position.x},${tree.position.y}"
    }

    override fun walkTo(tree: TreeCandidate) {
        steps += "walk to ${tree.position.x},${tree.position.y}"
    }

    override fun walkToLocation() {
        steps += "walk to location"
    }

    override fun tell(message: String) {
        steps += "tell $message"
    }
}
