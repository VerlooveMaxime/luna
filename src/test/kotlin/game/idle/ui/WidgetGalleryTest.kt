package game.idle.ui

import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetGalleryTest {

    private val gallery = WidgetGallery()

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun openGallery(): Player {
        val player = TestWorld.login("gallery", Position(3200, 3200))
        gallery.open(player)
        return player
    }

    /** Message types sent after the login itself (which assigns the player its index). */
    private fun types(player: Player): List<String> =
        TestWorld.messages(player).map { it.type }.filter { it != "AssignmentMessageWriter" }

    @Test
    fun `opening sends the opened line before the screen shows`() {
        val player = openGallery()

        assertEquals(listOf("WidgetTextMessageWriter", "InterfaceMessageWriter"), types(player))
        assertEquals(GalleryInterface.OPENED_TEXT, TestWorld.messages(player).first { it.type == "WidgetTextMessageWriter" }.fields["text"])
        assertEquals(GalleryWidgets.GALLERY, TestWorld.messages(player).last().fields["id"])
    }

    @Test
    fun `a tile answers in the chat box`() {
        val player = openGallery()

        gallery.click(player, GalleryWidgets.tileFace(2))

        assertEquals(listOf("Gallery: clicked tile 3."), TestWorld.chatbox(player))
    }

    @Test
    fun `hide hides the nested layer`() {
        val player = openGallery()

        gallery.click(player, GalleryWidgets.HIDE)

        val visibility = TestWorld.messages(player).single { it.type == "WidgetVisibilityMessageWriter" }
        assertEquals(GalleryWidgets.HIDEABLE, visibility.fields["id"])
        assertEquals(true, visibility.fields["hiddenUntilHovered"])
        assertEquals(listOf("Gallery: nested layer hidden."), TestWorld.chatbox(player))
    }

    @Test
    fun `show shows the nested layer again`() {
        val player = openGallery()

        gallery.click(player, GalleryWidgets.SHOW)

        val visibility = TestWorld.messages(player).single { it.type == "WidgetVisibilityMessageWriter" }
        assertEquals(false, visibility.fields["hiddenUntilHovered"])
        assertEquals(listOf("Gallery: nested layer shown."), TestWorld.chatbox(player))
    }

    @Test
    fun `close closes the gallery`() {
        val player = openGallery()

        gallery.click(player, GalleryWidgets.CLOSE)

        assertFalse(player.overlays.has(GalleryInterface::class.java))
    }

    @Test
    fun `a widget with nothing to do stays quiet`() {
        val player = openGallery()

        gallery.click(player, GalleryWidgets.OPENED_LINE)

        assertTrue(TestWorld.chatbox(player).isEmpty())
        assertTrue(player.overlays.has(GalleryInterface::class.java))
    }

    @Test
    fun `the gallery's range is owned, its neighbours are not`() {
        assertTrue(GalleryWidgets.owns(GalleryWidgets.FIRST_ID))
        assertTrue(GalleryWidgets.owns(GalleryWidgets.ID_LIMIT - 1))
        assertFalse(GalleryWidgets.owns(GalleryWidgets.FIRST_ID - 1))
        assertFalse(GalleryWidgets.owns(GalleryWidgets.ID_LIMIT))
    }

    @Test
    fun `a tile's face names its tile`() {
        assertEquals(7, GalleryWidgets.tile(GalleryWidgets.tileFace(7)))
    }

    @Test
    fun `any other widget is no tile`() {
        assertNull(GalleryWidgets.tile(GalleryWidgets.tileFace(0) + 1))
    }
}
