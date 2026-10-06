package game.idle.autopilot.making

import game.idle.autopilot.LunaClicks
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogue

/**
 * [Maker] for a logged-in player and one [recipe]. Combining posts the same event as the client's "use" of one item
 * on another, so Luna's skill runs unchanged; an option of the make window is answered the way Luna's own button
 * handler does it (close the window, then make that option so many times).
 */
class LunaMaker(private val player: Player, private val recipe: Recipe) : Maker {

    /** The make window is what this step answers, so it does not count as a window in the way. */
    override fun isBusy(): Boolean =
        LunaClicks.isActing(player) || (LunaClicks.hasBlockingWindow(player) && window() == null)

    override fun look(): MakeView {
        val window = window()
        return MakeView(
            useSlot = slotOf(recipe.use),
            onSlot = slotOf(recipe.on),
            windowOpen = window != null,
            productOption = window?.items?.indexOf(recipe.product)?.takeIf { it >= 0 },
        )
    }

    override fun use(useSlot: Int, onSlot: Int) {
        val event = ItemOnItemEvent(player, recipe.use, recipe.on, useSlot, onSlot, INVENTORY, INVENTORY)
        if (LunaClicks.mayAct(player, event)) {
            player.overlays.closeWindows(false)
            player.plugins.post(event)
        }
    }

    override fun choose(index: Int, times: Int) {
        val window = window() ?: return
        player.overlays.closeWindows()
        window.makeIndex(player, index, times)
    }

    override fun products(): Int = player.inventory.computeAmountForId(recipe.product)

    override fun stop() {
        player.actions.interruptWeak()
    }

    override fun tell(message: String) {
        player.sendMessage(message)
    }

    private fun window(): MakeItemDialogue? = player.overlays.getOverlay(MakeItemDialogue::class.java)

    private fun slotOf(id: Int): Int? = player.inventory.computeIndexForId(id).takeIf { it >= 0 }

    private companion object {
        const val INVENTORY = 3214
    }
}
