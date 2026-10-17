package game.idle.autopilot.cooking

import api.predef.cooking
import game.idle.autopilot.Candidate
import game.idle.autopilot.LunaClicks
import game.idle.autopilot.ObjectsAround
import game.idle.location.Area
import game.idle.location.Tile
import game.idle.movement.Doorways
import game.skill.cooking.cookFood.Cooking
import game.skill.cooking.cookFood.CookingInterface
import game.skill.cooking.cookFood.Food
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.UseItemEvent.ItemOnObjectEvent
import io.luna.game.model.mob.Player

/**
 * [Cooker] for a logged-in player cooking [rawIds] on fires and ranges within [area]. Using the food and picking
 * "cook all" go through the same interaction and button events as the client's clicks, so Luna's cooking runs
 * unchanged.
 */
class LunaCooker(private val player: Player, private val rawIds: Set<Int>, private val area: Area, doorways: Doorways) : Cooker {

    private val around = ObjectsAround(player, area, Cooking.COOKING_OBJECTS, doorways)

    /** The cooking window is the point of this step, so it does not count as a window in the way. */
    override fun isBusy(): Boolean = LunaClicks.busyBesides(player, cookingWindowOpen())

    override fun look(): CookingView =
        CookingView(
            rawSlot = (0 until player.inventory.capacity()).firstOrNull(::cookable),
            windowOpen = cookingWindowOpen(),
            atLocation = around.atLocation(),
            places = around.candidates(),
        )

    override fun useOn(place: Candidate, slot: Int) {
        val target = around.find(place) ?: return
        val item = player.inventory[slot] ?: return
        val event = ItemOnObjectEvent(player, item.id, slot, LunaClicks.INVENTORY, target)
        LunaClicks.interact(player, event, target, ItemOnObjectEvent::class.java)
    }

    override fun cookAll() = LunaClicks.post(player, ButtonClickEvent(player, COOK_ALL))

    override fun anchor(): Tile = area.anchor

    override fun raw(): Int = rawIds.sumOf { player.inventory.computeAmountForId(it) }

    override fun interrupt() = LunaClicks.interrupt(player)

    /**
     * Whether [slot] holds raw food of the step the player's Cooking level allows: Luna refuses the rest only once the
     * cooking window is answered, leaving the window open.
     */
    private fun cookable(slot: Int): Boolean {
        val id = player.inventory[slot]?.id ?: return false
        return id in rawIds && Food.RAW_TO_FOOD.getValue(id).lvl <= player.cooking.level
    }

    private fun cookingWindowOpen(): Boolean = player.overlays.has(CookingInterface::class.java)

    private companion object {
        /** The cooking window's "all" button (`cookFood.kts`). */
        const val COOK_ALL = 13717
    }
}
