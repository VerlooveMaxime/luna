package game.idle.autopilot.making

import game.idle.autopilot.EndlessAction
import game.idle.autopilot.LunaClicks
import game.idle.autopilot.RefusingController
import game.testworld.TestWorld
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.model.mob.overlay.StandardInterface
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaMakerTest {

    private val dough = BREAD_DOUGH
    private val pittaDough = 1863

    /** A make window like Luna's dough window that records what was asked of it. */
    private class RecordingWindow(vararg items: Int) : MakeItemDialogue(*items) {
        val made = mutableListOf<String>()

        override fun make(player: Player, id: Int, index: Int, forAmount: Int) {
            made += "$id x$forAmount"
        }
    }

    /** A window of buttons like Luna's glass blowing one. */
    private class ButtonsWindow : StandardInterface(11462)

    private val glass = simpleRecipe(229, "Vial", 1785, 1775, MakeWindow.Buttons(ButtonsWindow::class.java, BUTTON))

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
    fun `the view takes the first way the carried items allow`() {
        val player = login()
        player.inventory.add(Item(1933))
        player.inventory.add(Item(1937))
        val anyWater = BREAD_DOUGH.copy(ways = listOf(RecipeWay(1933, 1929, mapOf(1933 to 1, 1929 to 1)), RecipeWay(1933, 1937, mapOf(1933 to 1, 1937 to 1))))

        assertEquals(listOf(0, 1), LunaMaker(player, anyWater).look().let { listOf(it.useSlot, it.onSlot) })
    }

    @Test
    fun `a way taking more than is carried is not used`() {
        val player = login()
        player.inventory.add(Item(1733))
        player.inventory.add(Item(6289, 2))
        val boots = Recipe(6328, "Snakeskin boots", Skill.CRAFTING, 45, listOf(RecipeWay(1733, 6289, mapOf(6289 to 6), setOf(1733))))

        assertNull(LunaMaker(player, boots).look().useSlot)
    }

    @Test
    fun `a way without its tool is not used`() {
        val player = login()
        player.inventory.add(Item(1511))
        val shafts = Recipe(52, "Arrow shaft", Skill.FLETCHING, 1, listOf(RecipeWay(946, 1511, mapOf(1511 to 1), setOf(946))))

        assertNull(LunaMaker(player, shafts).look().useSlot)
    }

    @Test
    fun `combining an empty slot uses nothing`() {
        val player = login()
        holdIngredients(player)
        val used = mutableListOf<Int>()
        TestWorld.listen(ItemOnItemEvent::class.java) { used += it.usedItemId }

        maker(player).use(5, 1)
        maker(player).use(2, 5)

        assertEquals(emptyList<Int>(), used)
    }

    @Test
    fun `the recipe's window of buttons is not in the way, and offers the product's button`() {
        val player = login()
        player.overlays.open(ButtonsWindow())

        assertFalse(LunaMaker(player, glass).isBusy())
        assertEquals(MakeView(useSlot = null, onSlot = null, windowOpen = true, productOption = BUTTON), LunaMaker(player, glass).look())
    }

    @Test
    fun `without its window of buttons the recipe has no option`() {
        assertEquals(MakeView(useSlot = null, onSlot = null, windowOpen = false, productOption = null), LunaMaker(login(), glass).look())
    }

    @Test
    fun `choosing on a window of buttons clicks the product's button and leaves the window open`() {
        val player = login()
        player.overlays.open(ButtonsWindow())
        val clicked = mutableListOf<Int>()
        TestWorld.listen(ButtonClickEvent::class.java) { clicked += it.id }

        LunaMaker(player, glass).choose(BUTTON, 28)

        assertEquals(listOf(BUTTON), clicked)
        assertTrue(player.overlays.has(ButtonsWindow::class.java))
    }

    @Test
    fun `a button click the controller refuses is not made`() {
        val player = login()
        player.overlays.open(ButtonsWindow())
        player.controllers.register(RefusingController(player))
        val clicked = mutableListOf<Int>()
        TestWorld.listen(ButtonClickEvent::class.java) { clicked += it.id }

        LunaMaker(player, glass).choose(BUTTON, 28)

        assertEquals(emptyList<Int>(), clicked)
    }

    @Test
    fun `products count every item the recipe counts as made`() {
        val player = login()
        player.inventory.add(Item(1995, 2))
        player.inventory.add(Item(1993, 3))
        val wine = simpleRecipe(1995, "Unfermented wine", 1987, 1937).copy(made = setOf(1995, 1993))

        assertEquals(5, LunaMaker(player, wine).products())
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

    private companion object {
        const val BUTTON = 12398
    }
}
