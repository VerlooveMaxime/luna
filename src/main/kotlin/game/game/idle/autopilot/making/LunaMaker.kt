package game.idle.autopilot.making

import game.idle.autopilot.LunaClicks
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogue

/**
 * [Maker] for a logged-in player and one [recipe]. Combining posts the same event as the client's "use" of one item
 * on another, so Luna's skill runs unchanged. The window that opens is answered as the client would: an option of the
 * make window the way Luna's own button handler does it (close the window, then make that option so many times), a
 * window of buttons by clicking the product's button.
 */
class LunaMaker(private val player: Player, private val recipe: Recipe) : Maker {

    /** The make window is what this step answers, so it does not count as a window in the way. */
    override fun isBusy(): Boolean =
        LunaClicks.isActing(player) || (LunaClicks.hasBlockingWindow(player) && !makeWindowOpen())

    override fun look(): MakeView {
        val way = recipe.ways.firstOrNull(::carried)
        return MakeView(
            useSlot = way?.let { slotOf(it.use) },
            onSlot = way?.let { slotOf(it.on) },
            windowOpen = makeWindowOpen(),
            productOption = productOption(),
        )
    }

    override fun use(useSlot: Int, onSlot: Int) {
        val used = player.inventory[useSlot]?.id ?: return
        val target = player.inventory[onSlot]?.id ?: return
        val event = ItemOnItemEvent(player, used, target, useSlot, onSlot, INVENTORY, INVENTORY)
        if (LunaClicks.mayAct(player, event)) {
            player.overlays.closeWindows(false)
            player.plugins.post(event)
        }
    }

    override fun choose(index: Int, times: Int) {
        when (recipe.window) {
            is MakeWindow.Buttons -> {
                val click = ButtonClickEvent(player, index)
                if (LunaClicks.mayAct(player, click)) player.plugins.post(click)
            }
            else -> {
                val window = makeItemWindow() ?: return
                player.overlays.closeWindows()
                window.makeIndex(player, index, times)
            }
        }
    }

    override fun products(): Int = recipe.made.sumOf { player.inventory.computeAmountForId(it) }

    override fun stop() {
        player.actions.interruptWeak()
    }

    /** Whether the player carries everything [way] takes. */
    private fun carried(way: RecipeWay): Boolean =
        way.inputs.all { (id, count) -> player.inventory.computeAmountForId(id) >= count } &&
            way.tools.all { player.inventory.contains(it) }

    private fun makeItemWindow(): MakeItemDialogue? = player.overlays.getOverlay(MakeItemDialogue::class.java)

    private fun makeWindowOpen(): Boolean =
        makeItemWindow() != null || (recipe.window as? MakeWindow.Buttons)?.let { player.overlays.has(it.type) } == true

    /** The option of the open window that makes the product: its place in a make window, or its button. */
    private fun productOption(): Int? =
        when (val window = recipe.window) {
            is MakeWindow.Buttons -> window.button.takeIf { player.overlays.has(window.type) }
            else -> makeItemWindow()?.items?.indexOf(recipe.product)?.takeIf { it >= 0 }
        }

    /** A carried item's slot. */
    private fun slotOf(id: Int): Int = player.inventory.computeIndexForId(id)

    private companion object {
        const val INVENTORY = 3214
    }
}
