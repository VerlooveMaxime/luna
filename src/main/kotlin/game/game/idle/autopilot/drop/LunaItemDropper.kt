package game.idle.autopilot.drop

import game.idle.autopilot.LunaClicks
import io.luna.game.event.impl.DropItemEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/** [ItemDropper] for a logged-in player: drops every [itemIds] item through the same event as the client's "Drop". */
class LunaItemDropper(private val player: Player, private val itemIds: Set<Int>) : ItemDropper {

    override fun isBusy(): Boolean = LunaClicks.isActing(player)

    override fun hasItems(): Boolean = items().isNotEmpty()

    override fun dropItems() {
        for ((slot, item) in items()) {
            val event = DropItemEvent(player, item.id, INVENTORY_WIDGET, slot)
            if (LunaClicks.mayAct(player, event)) {
                player.plugins.post(event)
            }
        }
    }

    /** Slot and item of everything in the inventory the step drops. */
    private fun items(): List<Pair<Int, Item>> =
        (0 until player.inventory.capacity()).mapNotNull { slot ->
            player.inventory[slot]?.takeIf { it.id in itemIds }?.let { slot to it }
        }

    private companion object {
        const val INVENTORY_WIDGET = 3214
    }
}
