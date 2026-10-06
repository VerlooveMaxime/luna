package game.idle.autopilot.smithing

import api.predef.smithing
import game.idle.autopilot.LunaClicks
import game.idle.autopilot.PlaceCandidate
import game.idle.location.Area
import game.idle.movement.ReachScan
import game.idle.movement.ReachTerrain
import game.idle.movement.footprintOf
import game.skill.smithing.BarType
import game.skill.smithing.Smithing
import game.skill.smithing.smithBar.SmithingInterface
import game.skill.smithing.smithBar.SmithingTable
import io.luna.game.event.impl.UseItemEvent.ItemOnObjectEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemSecondClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemThirdClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE
import io.luna.game.model.`object`.GameObject

/**
 * [Smither] for a logged-in player smithing the [table] item of [metal] at anvils within [area]. The bar is used on
 * the anvil and the item clicked in the smithing window through the same events as the client's, so Luna's smithing
 * runs unchanged.
 */
class LunaSmither(
    private val player: Player,
    private val metal: BarType,
    private val table: SmithingTable,
    private val area: Area,
) : Smither, ReachTerrain<GameObject> {

    private val world get() = player.world
    private val anchor: Position = area.anchor.toPosition()
    private val scan = ReachScan(anchor, area.radius)
    private val itemId: Int = table.items.first { it.barType == metal }.item.id

    /** The smithing window is the point of this step, so it does not count as a window in the way. */
    override fun isBusy(): Boolean =
        LunaClicks.isActing(player) || (LunaClicks.hasBlockingWindow(player) && !smithingWindowOpen())

    override fun look(): SmithingView =
        SmithingView(
            barSlot = barSlot(),
            hasHammer = player.inventory.computeAmountForId(Smithing.HAMMER) > 0,
            smithingLevel = player.smithing.level,
            windowOpen = smithingWindowOpen(),
            atLocation = scan.atLocation(player.position),
            anvils = scan.reachable(player.position, anvils(), ::footprintOf, terrain = this)
                .map { (anvil, reach) -> PlaceCandidate.of(anvil, reach) },
        )

    override fun useOn(anvil: PlaceCandidate, slot: Int) {
        val target = find(anvil) ?: return
        val event = ItemOnObjectEvent(player, metal.id, slot, INVENTORY, target)
        LunaClicks.interact(player, event, target, ItemOnObjectEvent::class.java)
    }

    override fun choose(times: Int) {
        val event = when (times) {
            1 -> WidgetItemFirstClickEvent(player, table.slotId, table.widgetId, itemId)
            5 -> WidgetItemSecondClickEvent(player, table.slotId, table.widgetId, itemId)
            else -> WidgetItemThirdClickEvent(player, table.slotId, table.widgetId, itemId)
        }
        if (LunaClicks.mayAct(player, event)) {
            player.plugins.post(event)
        }
    }

    override fun walkTo(anvil: PlaceCandidate) = walkTo(anvil.approach)

    override fun walkToLocation() = walkTo(anchor)

    private fun walkTo(tile: Position) {
        player.overlays.closeWindows(false)
        player.navigator.navigate(tile, true)
    }

    override fun made(): Int = player.inventory.computeAmountForId(itemId)

    override fun stop() {
        player.actions.interruptWeak()
    }

    override fun tell(message: String) {
        player.sendMessage(message)
    }

    /** The slot of the bar while there are enough for one item. */
    private fun barSlot(): Int? =
        if (player.inventory.computeAmountForId(metal.id) >= table.bars) player.inventory.computeIndexForId(metal.id) else null

    private fun smithingWindowOpen(): Boolean = player.overlays.has(SmithingInterface::class.java)

    // Objects found through their chunk are always ACTIVE, so only the id needs checking.
    private fun anvils(): List<GameObject> =
        world.locator.findObjects(anchor, area.radius) { it.id in Smithing.ANVIL_OBJECTS && it.isVisibleTo(player) }.toList()

    private fun find(anvil: PlaceCandidate): GameObject? =
        world.objects.findAll(anvil.position)
            .filter { it.id == anvil.objectId && it.isVisibleTo(player) }
            .findFirst().orElse(null)

    override fun canStep(from: Position, direction: Direction): Boolean =
        world.collisionManager.traversable(from, EntityType.PLAYER, direction)

    override fun reachedFrom(tile: Position, target: GameObject): Boolean =
        world.collisionManager.reached(tile, target, STANDARD_SIZE)

    private companion object {
        const val INVENTORY = 3214
    }
}
