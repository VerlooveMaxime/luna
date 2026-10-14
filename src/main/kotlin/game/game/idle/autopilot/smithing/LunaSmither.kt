package game.idle.autopilot.smithing

import game.idle.autopilot.Candidate
import game.idle.autopilot.LunaClicks
import game.idle.autopilot.ObjectsAround
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.smithing.BarType
import game.skill.smithing.Smithing
import game.skill.smithing.smithBar.SmithingInterface
import game.skill.smithing.smithBar.SmithingTable
import io.luna.game.event.impl.UseItemEvent.ItemOnObjectEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemSecondClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemThirdClickEvent
import io.luna.game.model.mob.Player

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
) : Smither {

    private val around = ObjectsAround(player, area, Smithing.ANVIL_OBJECTS)
    private val itemId: Int = table.items.first { it.barType == metal }.item.id

    /** The smithing window is the point of this step, so it does not count as a window in the way. */
    override fun isBusy(): Boolean = LunaClicks.busyBesides(player, smithingWindowOpen())

    override fun look(): SmithingView =
        SmithingView(
            barSlot = barSlot(),
            windowOpen = smithingWindowOpen(),
            atLocation = around.atLocation(),
            anvils = around.candidates(),
        )

    override fun useOn(anvil: Candidate, slot: Int) {
        val target = around.find(anvil) ?: return
        val event = ItemOnObjectEvent(player, metal.id, slot, LunaClicks.INVENTORY, target)
        LunaClicks.interact(player, event, target, ItemOnObjectEvent::class.java)
    }

    override fun choose(times: Int) {
        val event = when (times) {
            1 -> WidgetItemFirstClickEvent(player, table.slotId, table.widgetId, itemId)
            5 -> WidgetItemSecondClickEvent(player, table.slotId, table.widgetId, itemId)
            else -> WidgetItemThirdClickEvent(player, table.slotId, table.widgetId, itemId)
        }
        LunaClicks.post(player, event)
    }

    override fun walkTo(anvil: Candidate) = LunaClicks.walk(player, anvil.approach)

    override fun anchor(): Tile = area.anchor

    override fun made(): Int = player.inventory.computeAmountForId(itemId)

    override fun interrupt() = LunaClicks.interrupt(player)

    /** The slot of the bar while there are enough for one item. */
    private fun barSlot(): Int? =
        if (player.inventory.computeAmountForId(metal.id) >= table.bars) player.inventory.computeIndexForId(metal.id) else null

    private fun smithingWindowOpen(): Boolean = player.overlays.has(SmithingInterface::class.java)
}
