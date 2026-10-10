package game.idle.autopilot.fighting

/** Records each step as text, for example `attack 3100,9510`. */
class FakeFighter(var view: FightView) : Fighter {

    var busy = false
    var kills = 0
    val steps = mutableListOf<String>()

    override fun isBusy(): Boolean = busy

    override fun look(): FightView = view

    override fun attack(target: TargetCandidate) {
        steps += "attack ${target.position.x},${target.position.y}"
    }

    override fun walkTo(target: TargetCandidate) {
        steps += "walk to ${target.position.x},${target.position.y}"
    }

    override fun walkToLocation() {
        steps += "walk to location"
    }

    override fun kills(): Int = kills

    override fun stop() {
        steps += "stop"
    }
}
