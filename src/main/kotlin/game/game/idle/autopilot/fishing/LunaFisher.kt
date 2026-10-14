package game.idle.autopilot.fishing

import game.idle.autopilot.Candidate
import game.idle.autopilot.LunaClicks
import game.idle.autopilot.NpcsAround
import game.idle.location.Area
import game.idle.location.Tile
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcSecondClickEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE

/**
 * [Fisher] for a logged-in player fishing with [method] within [area]. A cast goes through the same interaction as the
 * client's click on the spot's option for the method, so Luna's reach check and fishing run unchanged.
 */
class LunaFisher(private val player: Player, private val method: FishingMethod, private val area: Area) : Fisher {

    private val around = NpcsAround(player, area, accepts = { it.id in method.spotIds }, policy = { STANDARD_SIZE })

    /**
     * Out of bait, the player is never busy: Luna's fishing then opens a dialogue that would hold the step, and the
     * step has to block on the bait.
     */
    override fun isBusy(): Boolean = LunaClicks.busy(player) && hasBait()

    override fun look(): FishingView = FishingView(around.atLocation(), around.candidates())

    /** A spot that moved away since the look is not clicked, nor whatever npc took its index. */
    override fun fish(spot: Candidate) {
        val npc = around.find(spot)?.takeIf { it.id in method.spotIds && it.position == spot.position } ?: return
        if (npc.id in method.secondClickSpots) {
            LunaClicks.interact(player, NpcSecondClickEvent(player, npc), npc, NpcSecondClickEvent::class.java)
        } else {
            LunaClicks.interact(player, NpcFirstClickEvent(player, npc), npc, NpcFirstClickEvent::class.java)
        }
    }

    override fun walkTo(spot: Candidate) = LunaClicks.walk(player, spot.approach)

    override fun anchor(): Tile = area.anchor

    override fun catches(): Int = method.catchIds.sumOf { player.inventory.computeAmountForId(it) }

    override fun interrupt() = LunaClicks.interrupt(player)

    private fun hasBait(): Boolean = method.tool.bait?.let { player.inventory.contains(it) } ?: true
}
