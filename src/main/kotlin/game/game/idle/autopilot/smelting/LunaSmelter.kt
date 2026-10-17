package game.idle.autopilot.smelting

import game.idle.autopilot.Candidate
import game.idle.autopilot.LunaClicks
import game.idle.autopilot.ObjectsAround
import game.idle.location.Area
import game.idle.location.Tile
import game.idle.movement.Doorways
import game.skill.smithing.BarType
import game.skill.smithing.Smithing
import io.luna.game.event.impl.UseItemEvent.ItemOnObjectEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * [Smelter] for a logged-in player smelting [bar] at furnaces within [area]. The ore is used on the furnace through
 * the same interaction as the client's, so Luna's smelting runs unchanged and smelts every bar the ores make.
 */
class LunaSmelter(private val player: Player, private val bar: BarType, private val area: Area, doorways: Doorways) : Smelter {

    private val around = ObjectsAround(player, area, Smithing.FURNACE_OBJECTS, doorways)

    /** Luna smelts the bar its ore names; iron ore names iron, so steel is smelted from its coal. */
    private val oreToUse: Int = if (bar == BarType.STEEL) COAL else bar.oreRequired.first.id

    override fun isBusy(): Boolean = LunaClicks.busy(player)

    override fun look(): SmeltingView =
        SmeltingView(
            oreSlot = if (hasOreForABar()) player.inventory.computeIndexForId(oreToUse) else null,
            atLocation = around.atLocation(),
            furnaces = around.candidates(),
        )

    override fun smelt(furnace: Candidate, slot: Int) {
        val target = around.find(furnace) ?: return
        val ore = player.inventory[slot] ?: return
        val event = ItemOnObjectEvent(player, ore.id, slot, LunaClicks.INVENTORY, target)
        LunaClicks.interact(player, event, target, ItemOnObjectEvent::class.java)
    }

    override fun anchor(): Tile = area.anchor

    override fun bars(): Int = player.inventory.computeAmountForId(bar.id)

    override fun interrupt() = LunaClicks.interrupt(player)

    private fun hasOreForABar(): Boolean = listOfNotNull(bar.oreRequired.first, bar.oreRequired.second).all(::carries)

    private fun carries(ore: Item): Boolean = player.inventory.computeAmountForId(ore.id) >= ore.amount

    private companion object {
        const val COAL = 453
    }
}
