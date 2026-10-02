package game.idle.tutorial

import game.harness.RecordedMessage
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ItemBoxTest {

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun shown(items: List<Int>, lines: List<String>): Player {
        val player = TestWorld.login("reader", Position(3200, 3200))
        player.newDialogue().add(ItemBox(items, lines)).open()
        return player
    }

    private fun sent(player: Player, type: String): List<RecordedMessage> = TestWorld.messages(player).filter { it.type == type }

    @Test
    fun `one item and one line use objbox1`() {
        assertEquals(306, ItemBoxLayout.of(items = 1, lines = 1).interfaceId)
    }

    @Test
    fun `one item and four lines use objbox4, top line first`() {
        assertEquals(listOf(324, 323, 326, 327), ItemBoxLayout.of(items = 1, lines = 4).texts)
    }

    @Test
    fun `two items use doubleobjbox`() {
        assertEquals(4950, ItemBoxLayout.of(items = 2, lines = 2).interfaceId)
    }

    @Test
    fun `two items and one line leave the other lines blank`() {
        assertEquals(listOf(4953, 4955, 4956), ItemBoxLayout.of(items = 2, lines = 1).blanks)
    }

    @Test
    fun `a box without lines is refused`() {
        assertThrows<IllegalArgumentException> { ItemBoxLayout.of(items = 1, lines = 0) }
    }

    @Test
    fun `a box with five lines is refused`() {
        assertThrows<IllegalArgumentException> { ItemBoxLayout.of(items = 1, lines = 5) }
    }

    @Test
    fun `a box with three items is refused`() {
        assertThrows<IllegalArgumentException> { ItemBoxLayout.of(items = 3, lines = 1) }
    }

    @Test
    fun `the box opens its interface in the chatbox`() {
        val player = shown(listOf(1351), listOf("The Survival Guide gives you a", "@blu@Bronze Hatchet!"))

        assertEquals(310, sent(player, "DialogueInterfaceMessageWriter").single().fields["id"])
    }

    @Test
    fun `each item is drawn in its model`() {
        val player = shown(listOf(590, 1351), listOf("Both."))

        val models = sent(player, "WidgetItemModelMessageWriter").map { it.fields.getValue("widgetId") to it.fields.getValue("itemId") }

        assertEquals(listOf(4951 to 590, 4957 to 1351), models)
    }

    @Test
    fun `the lines go to their widgets and the others are emptied`() {
        val player = shown(listOf(590, 1351), listOf("Both."))

        val texts = sent(player, "WidgetTextMessageWriter").associate { it.fields.getValue("id") to it.fields.getValue("text") }

        assertEquals(mapOf(4952 to "Both.", 4953 to "", 4955 to "", 4956 to ""), texts)
    }
}
