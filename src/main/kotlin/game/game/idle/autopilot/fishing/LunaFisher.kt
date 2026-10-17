package game.idle.autopilot.fishing

import game.idle.autopilot.Candidate
import game.idle.autopilot.LunaClicks
import game.idle.autopilot.NpcsAround
import game.idle.location.Area
import game.idle.location.Tile
import game.idle.movement.Doorways
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcSecondClickEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE

/**
 * [Fisher] for a logged-in player fishing for [catch] within [area]. A cast goes through the same interaction as the
 * client's click on the spot's option for the catch's method, so Luna's reach check and fishing run unchanged.
 */
class LunaFisher(private val player: Player, private val catch: FishingCatch, private val area: Area, doorways: Doorways) : Fisher {

    private val method = catch.method

    private val around = NpcsAround(player, area, accepts = { it.id in method.spotIds }, policy = { STANDARD_SIZE }, doorways)

    /**
     * An open window keeps the player busy, except the dialogue in which Luna's fishing says why it stopped: without the
     * tool or the bait, or with a full bag, the step has to block on the tool or the bait, or end on the full bag.
     */
    override fun isBusy(): Boolean = LunaClicks.isActing(player) || (LunaClicks.hasBlockingWindow(player) && canGoOn())

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

    override fun walkTo(spot: Candidate) = LunaClicks.approach(player, spot)

    override fun anchor(): Tile = area.anchor

    /** Only the fish the step is for: what else the method pulls up is by-catch (Maxime, 2026-10-15). */
    override fun catches(): Int = player.inventory.computeAmountForId(catch.fish.id)

    override fun pulledUp(): Int = method.catchIds.sumOf { player.inventory.computeAmountForId(it) }

    override fun interrupt() = LunaClicks.interrupt(player)

    private fun canGoOn(): Boolean = player.inventory.contains(method.tool.id) && hasBait() && !player.inventory.isFull

    private fun hasBait(): Boolean = method.tool.bait?.let { player.inventory.contains(it) } ?: true
}
