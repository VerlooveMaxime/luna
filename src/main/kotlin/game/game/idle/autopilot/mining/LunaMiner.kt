package game.idle.autopilot.mining

import game.idle.autopilot.Candidate
import game.idle.autopilot.LunaClicks
import game.idle.autopilot.ObjectsAround
import game.idle.location.Area
import game.idle.location.Tile
import game.idle.movement.Doorways
import game.skill.mining.Ore
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.mob.Player

/**
 * [Miner] for a logged-in player mining [ore] within [area]. Rocks are clicked through the same interaction as the
 * client's "Mine", so Luna's mining runs unchanged; an emptied rock turns into another object and drops out of the
 * search until it fills again.
 */
class LunaMiner(private val player: Player, private val ore: Ore, private val area: Area, doorways: Doorways) : Miner {

    private val around = ObjectsAround(player, area, Ore.ORE_MAP.get(ore), doorways)

    override fun isBusy(): Boolean = LunaClicks.busy(player)

    override fun look(): MiningView = MiningView(around.atLocation(), around.candidates())

    override fun mine(rock: Candidate) {
        val target = around.find(rock) ?: return
        LunaClicks.interact(player, ObjectFirstClickEvent(player, target), target, ObjectFirstClickEvent::class.java)
    }

    override fun anchor(): Tile = area.anchor

    override fun ores(): Int = player.inventory.computeAmountForId(ore.item)

    override fun interrupt() = LunaClicks.interrupt(player)
}
