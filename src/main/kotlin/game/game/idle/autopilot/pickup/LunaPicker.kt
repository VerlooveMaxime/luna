package game.idle.autopilot.pickup

import game.idle.autopilot.LunaClicks
import game.idle.location.Area
import game.idle.movement.ReachScan
import game.idle.movement.TileBounds
import game.idle.movement.WalkingDistances
import io.luna.game.event.impl.GroundItemClickEvent.PickupItemEvent
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionAction

/**
 * [Picker] for a logged-in player taking [itemIds] lying within [area]: the items Luna lets them see (their own drops
 * and loot, others' once they turn public), on a tile they can walk onto, as Luna's pickup wants them on the item's tile.
 * Takes go through the same event as the client's "Take", so Luna's checks, sound and full-bag line apply unchanged.
 */
class LunaPicker(private val player: Player, private val itemIds: Set<Int>, private val area: Area) : Picker {

    private val world get() = player.world
    private val anchor: Position = area.anchor.toPosition()
    private val bounds = TileBounds.around(anchor, area.radius)

    /** The id of the item taken last and how many of it the bag held then, until the take is over. */
    private var taking: Pair<Int, Int>? = null
    private var pickedUp = 0

    override fun isBusy(): Boolean =
        !player.walking.isEmpty || player.actions.first(InteractionAction::class.java) != null || LunaClicks.hasBlockingWindow(player)

    override fun look(): PickUpView {
        val here = player.position
        if (here.z != anchor.z || here.computeLongestDistance(anchor) > ReachScan.MAX_WALK) {
            return PickUpView(atLocation = false, items = emptyList())
        }
        val distances = WalkingDistances.from(here, bounds.including(here)) { from, direction ->
            world.collisionManager.traversable(from, EntityType.PLAYER, direction)
        }
        val items = lying().mapNotNull { item ->
            distances[item.position]?.let { GroundLoot(item.id, item.position, it, fits = player.inventory.hasSpaceFor(item.toItem())) }
        }
        return PickUpView(atLocation = here in bounds, items = items)
    }

    override fun take(item: GroundLoot) {
        val ground = find(item) ?: return
        taking = ground.id to player.inventory.computeAmountForId(ground.id)
        LunaClicks.interact(player, PickupItemEvent(player, ground), ground, PickupItemEvent::class.java)
    }

    override fun walkTo(item: GroundLoot) = walk(item.position)

    override fun walkToLocation() = walk(anchor)

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

    override fun tell(message: String) {
        player.sendMessage(message)
    }

    private fun walk(to: Position) {
        player.overlays.closeWindows(false)
        player.navigator.navigate(to, true)
    }

    private fun lying(): Set<GroundItem> = world.locator.findItems(anchor, area.radius) { it.id in itemIds && it.isVisibleTo(player) }

    /** The item the look saw, if it still lies there for the player. */
    private fun find(item: GroundLoot): GroundItem? =
        world.items.findAll(item.position).filter { it.id == item.id && it.isVisibleTo(player) }.findFirst().orElse(null)
}
