package game.idle.autopilot.fighting

import game.idle.autopilot.Candidate
import game.idle.location.Tile

/** Records each step as text, for example `attack 3100,9510`; [anchor] is the step's area anchor. */
class FakeFighter(var view: FightView, private val anchor: Tile = Tile(3200, 3200)) : Fighter {

    var busy = false
    var kills = 0
    var lastKillDying = false
    val steps = mutableListOf<String>()

    override fun isBusy(): Boolean = busy

    override fun look(): FightView = view

    override fun attack(npc: Candidate) {
        steps += "attack ${npc.position.x},${npc.position.y}"
    }

    override fun walkTo(npc: Candidate) {
        steps += "walk to ${npc.position.x},${npc.position.y}"
    }

    override fun anchor(): Tile = anchor

    override fun kills(): Int = kills

    override fun lastKillDying(): Boolean = lastKillDying

    override fun interrupt() {
        steps += "interrupt"
    }
}
