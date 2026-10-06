package game.idle.autopilot.making

import game.idle.autopilot.EndlessAction
import game.idle.autopilot.LunaClicks
import game.idle.autopilot.RefusingController
import game.testworld.TestWorld
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.model.mob.overlay.StandardInterface
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaMakerTest {

    private val dough = Recipe(2307, "bread dough", 1933, 1929)
    private val pittaDough = 1863

    /** A make window like Luna's dough window that records what was asked of it. */
    private class RecordingWindow(vararg items: Int) : MakeItemDialogue(*items) {
        val made = mutableListOf<String>()

        override fun make(player: Player, id: Int, index: Int, forAmount: Int) {
            made += "$id x$forAmount"
        }
    }

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(): Player = TestWorld.login("maker", Position(3200, 3200))

    private fun maker(player: Player) = LunaMaker(player, dough)

    private fun holdIngredients(player: Player) {
        player.inventory.add(Item(1351))
        player.inventory.add(Item(1929))
        player.inventory.add(Item(1933))
    }

    @Test
    fun `an idle player is not busy`() {
        assertFalse(maker(login()).isBusy())
    }

    @Test
    fun `a walking player is busy`() {
        val player = login()
        player.walking.addStep(Direction.EAST)

        assertTrue(maker(player).isBusy())
    }

    @Test
    fun `an open window keeps the player busy, the make window does not`() {
        val other = login()
        other.overlays.open(StandardInterface(5292))
        val making = TestWorld.login("other", Position(3205, 3205))
        making.overlays.open(RecordingWindow(pittaDough, dough.product))

        assertTrue(maker(other).isBusy())
        assertFalse(maker(making).isBusy())
    }

    @Test
    fun `the view finds both ingredients`() {
        val player = login()
        holdIngredients(player)

        assertEquals(MakeView(useSlot = 2, onSlot = 1, windowOpen = false, productOption = null), maker(player).look())
    }

    @Test
    fun `the view finds the product among the open window's options`() {
        val player = login()
        player.overlays.open(RecordingWindow(pittaDough, dough.product))

        assertEquals(MakeView(useSlot = null, onSlot = null, windowOpen = true, productOption = 1), maker(player).look())
    }

    @Test
    fun `a window without the product offers no option`() {
        val player = login()
        player.overlays.open(RecordingWindow(pittaDough))

        assertEquals(MakeView(useSlot = null, onSlot = null, windowOpen = true, productOption = null), maker(player).look())
    }

    @Test
    fun `combining uses one item on the other like the client`() {
        val player = login()
        holdIngredients(player)
        val used = mutableListOf<String>()
        TestWorld.listen(ItemOnItemEvent::class.java) { used += "${it.usedItemId} ${it.usedItemIndex} on ${it.targetItemId} ${it.targetItemIndex}" }

        maker(player).use(2, 1)

        assertEquals(listOf("1933 2 on 1929 1"), used)
    }

    @Test
    fun `a click the controller refuses combines nothing`() {
        val player = login()
        holdIngredients(player)
        player.controllers.register(RefusingController(player))
        val used = mutableListOf<Int>()
        TestWorld.listen(ItemOnItemEvent::class.java) { used += it.usedItemId }

        maker(player).use(2, 1)

        assertEquals(emptyList<Int>(), used)
    }

    @Test
    fun `choosing closes the window and makes that option so many times`() {
        val player = login()
        val window = RecordingWindow(pittaDough, dough.product)
        player.overlays.open(window)

        maker(player).choose(1, 28)

        assertEquals(listOf("2307 x28"), window.made)
        assertFalse(player.overlays.has(MakeItemDialogue::class.java))
    }

    @Test
    fun `choosing without a window does nothing`() {
        val player = login()

        maker(player).choose(0, 28)

        assertFalse(player.overlays.has(MakeItemDialogue::class.java))
    }

    @Test
    fun `products count what the recipe makes`() {
        val player = login()
        player.inventory.add(Item(dough.product, 3))
        player.inventory.add(Item(pittaDough))

        assertEquals(3, maker(player).products())
    }

    @Test
    fun `stopping ends the making in progress`() {
        val player = login()
        player.submitAction(EndlessAction(player))
        TestWorld.tick()

        maker(player).stop()
        TestWorld.tick()

        assertFalse(LunaClicks.isActing(player))
    }

    @Test
    fun `telling the player sends a chat box line`() {
        val player = login()

        maker(player).tell("No flour.")

        assertEquals(listOf("No flour."), TestWorld.chatbox(player))
    }
}
