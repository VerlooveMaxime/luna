package game.idle.autopilot.fishing

import api.predef.fishing
import game.idle.autopilot.LunaClicks
import game.idle.location.Area
import game.idle.movement.Footprint
import game.idle.movement.ReachScan
import game.idle.movement.ReachTerrain
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcSecondClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE

/**
 * [Fisher] for a logged-in player fishing with [method] within [area]. Spots are searched around the area's anchor
 * and ranked by walking distance; a cast goes through the same interaction as the client's click on the spot's option
 * for the method, so Luna's reach check and fishing run unchanged.
 */
class LunaFisher(private val player: Player, private val method: FishingMethod, private val area: Area) : Fisher, ReachTerrain<Npc> {

    private val world get() = player.world
    private val anchor: Position = area.anchor.toPosition()
    private val scan = ReachScan(anchor, area.radius)

    /**
     * Out of bait, the player is never busy: Luna's fishing then opens a dialogue that would hold the step, and the
     * step has to say why it stopped.
     */
    override fun isBusy(): Boolean = (LunaClicks.isActing(player) || LunaClicks.hasBlockingWindow(player)) && hasBait()

    override fun look(): FishingView =
        FishingView(
            hasTool = player.inventory.contains(method.tool.id),
            hasLevel = player.fishing.level >= method.tool.level,
            hasBait = hasBait(),
            inventoryFull = player.inventory.isFull,
            atLocation = scan.atLocation(player.position),
            spots = scan.reachable(player.position, spots(), { Footprint(it.position, it.size(), it.size()) }, terrain = this)
                .map { (npc, reach) -> SpotCandidate(npc.index, npc.position, reach.distance, reach.usableFromHere, reach.approach) },
        )

    override fun fish(spot: SpotCandidate) {
        val npc = find(spot) ?: return
        if (npc.id in method.secondClickSpots) {
            LunaClicks.interact(player, NpcSecondClickEvent(player, npc), npc, NpcSecondClickEvent::class.java)
        } else {
            LunaClicks.interact(player, NpcFirstClickEvent(player, npc), npc, NpcFirstClickEvent::class.java)
        }
    }

    override fun walkTo(spot: SpotCandidate) = walkTo(spot.approach)

    override fun walkToLocation() = walkTo(anchor)

    private fun walkTo(tile: Position) {
        player.overlays.closeWindows(false)
        player.navigator.navigate(tile, true)
    }

    override fun catches(): Int = method.catchIds.sumOf { player.inventory.computeAmountForId(it) }

    override fun stop() {
        player.actions.interruptWeak()
    }

    override fun tell(message: String) {
        player.sendMessage(message)
    }

    private fun hasBait(): Boolean = method.tool.bait?.let { player.inventory.contains(it) } ?: true

    private fun spots(): List<Npc> = world.locator.findNpcs(anchor, area.radius) { it.id in method.spotIds }.toList()

    /** A spot that moved away since the look is not clicked. */
    private fun find(spot: SpotCandidate): Npc? =
        world.npcs.get(spot.npcIndex)?.takeIf { it.id in method.spotIds && it.position == spot.position }

    override fun canStep(from: Position, direction: Direction): Boolean =
        world.collisionManager.traversable(from, EntityType.PLAYER, direction)

    override fun reachedFrom(tile: Position, target: Npc): Boolean = world.collisionManager.reached(tile, target, STANDARD_SIZE)
}
