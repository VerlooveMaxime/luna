package game.idle.autopilot.pickup

import game.idle.autopilot.Candidate
import game.idle.autopilot.LunaClicks
import game.idle.location.Area
import game.idle.movement.Doorways
import game.idle.movement.ReachScan
import io.luna.game.event.impl.GroundItemClickEvent.PickupItemEvent
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionAction

/**
 * [Picker] for a logged-in player taking [itemIds] lying within [area]: the items Luna lets them see (their own drops
 * and loot, others' once they turn public), on a tile they can walk onto, as Luna's pickup wants them on the item's tile.
 * Takes go through the same event as the client's "Take", so Luna's checks, sound and full-bag line apply unchanged.
 */
class LunaPicker(private val player: Player, private val itemIds: Set<Int>, private val area: Area, doorways: Doorways) : Picker {

    private val world get() = player.world
    private val anchor: Position = area.anchor.toPosition()
    private val scan = ReachScan(anchor, area.radius)
    private val steps = LunaClicks.reachSteps(world, doorways)

    /** The id of the item taken last and how many of it the bag held then, until the take is over. */
    private var taking: Pair<Int, Int>? = null
    private var pickedUp = 0

    override fun isBusy(): Boolean = LunaClicks.interacting(player) || LunaClicks.hasBlockingWindow(player)

    override fun look(): PickUpView {
        val here = player.position
        val distances = scan.distancesFrom(here, steps::step)
            ?: return PickUpView(atLocation = false, items = emptyList())
        val items = lying().mapNotNull { item ->
            distances[item.position]?.let { distance ->
                val fits = player.inventory.hasSpaceFor(item.toItem())
                val door = distances.doorOn(item.position)
                Candidate(item.id, item.position, distance, usableFromHere = distance == 0, approach = item.position, fits = fits, door = door)
            }
        }
        return PickUpView(atLocation = scan.atLocation(here), items = items)
    }

    override fun take(item: Candidate) {
        val ground = find(item) ?: return
        taking = ground.id to player.inventory.computeAmountForId(ground.id)
        LunaClicks.interact(player, PickupItemEvent(player, ground), ground, PickupItemEvent::class.java)
    }

    override fun walkTo(item: Candidate) = LunaClicks.approach(player, item)

    override fun walkToLocation() = LunaClicks.walk(player, anchor)

    /**
     * Counts the last take once it is over and the bag holds more of its item: someone faster gets nothing. Not by the
     * clicked item leaving the ground: of two piles alike on a tile, Luna takes the first off the ground, either one.
     */
    override fun pickedUp(): Int {
        val (id, held) = taking ?: return pickedUp
        if (player.actions.first(InteractionAction::class.java) != null) return pickedUp
        if (player.inventory.computeAmountForId(id) > held) pickedUp++
        taking = null
        return pickedUp
    }

    override fun interrupt() = LunaClicks.interrupt(player)

    private fun lying(): Set<GroundItem> = world.locator.findItems(anchor, area.radius) { it.id in itemIds && it.isVisibleTo(player) }

    /** The item the look saw, if it still lies there for the player. */
    private fun find(item: Candidate): GroundItem? =
        world.items.findAll(item.position).filter { it.id == item.id && it.isVisibleTo(player) }.findFirst().orElse(null)
}
