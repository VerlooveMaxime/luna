package game.idle.ui

import game.harness.RecordedMessage
import game.idle.flow.SavedFlows
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.StepOption
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

    private val chop = GalleryKind("chop", WidgetPicture.Media("staticons", 17))
    private val bank = GalleryKind("bank", WidgetPicture.Media("mapfunction", 5))
    private val font = ClientFont(IntArray(256) { 5 })
    private val gallery = WidgetGallery(listOf(chop, bank), { "npc $it" }, emptyList(), font)

    private val oak = StepOption("oak", "Oak", OptionIcon.Item(1521))
    private val trees = GallerySearch("Which tree?") { listOf(oak) }
    private val searching = WidgetGallery(listOf(chop, bank), { "npc $it" }, listOf(trees), font)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(): Player = TestWorld.login("gallery", Position(3200, 3200))

    private fun openGallery(): Player = login().also { gallery.open(it) }

    private fun messages(player: Player, type: String): List<RecordedMessage> = TestWorld.messages(player).filter { it.type == type }

    /** The last text sent per widget id. */
    private fun texts(player: Player): Map<Int, String> =
        messages(player, "WidgetTextMessageWriter").associate { it.fields.getValue("id") as Int to it.fields.getValue("text").toString() }

    /** The last picture sent per widget id, as source, name and id. */
    private fun pictures(player: Player): Map<Int, List<Any>> =
        messages(player, "PictureMessageWriter").associate {
            it.fields.getValue("widgetId") as Int to listOf(it.fields.getValue("source"), it.fields.getValue("name"), it.fields.getValue("id"))
        }

    /** The gallery open, a name given on its name prompt. */
    private fun named(name: String): Player {
        val player = openGallery()
        gallery.click(player, GalleryWidgets.NAME)
        SearchPrompts.name(player, checkNotNull(SearchPrompts.opened(player, NamePrompt::class.java)).serial, name)
        return player
    }

    /** The last prompt opened: its mode, title and the text its line starts with. */
    private fun opening(player: Player): List<Any> =
        messages(player, "SearchOpenMessageWriter").last().fields.let { listOf(it.getValue("mode"), it.getValue("title"), it.getValue("text")) }

    @Test
    fun `opening fills the gallery before the screen shows`() {
        val player = openGallery()

        assertEquals("InterfaceMessageWriter", TestWorld.messages(player).last().type)
        assertEquals(GalleryWidgets.GALLERY, TestWorld.messages(player).last().fields["id"])
        assertEquals(WidgetGallery.OPENED_TEXT, texts(player)[GalleryWidgets.OPENED_LINE])
    }

    @Test
    fun `every size of a kind's icon shows that kind's icon`() {
        val player = openGallery()

        assertEquals(listOf<Any>(PictureEncoding.MEDIA, "mapfunction", 5), pictures(player)[GalleryWidgets.icon(2, 1)])
    }

    @Test
    fun `icons past the last kind are left empty`() {
        val player = openGallery()

        assertEquals(listOf<Any>(PictureEncoding.NONE, "", 0), pictures(player)[GalleryWidgets.icon(0, 11)])
    }

    @Test
    fun `the items show at both sizes`() {
        val player = openGallery()

        assertEquals(listOf<Any>(PictureEncoding.ITEM, "", 1519), pictures(player)[GalleryWidgets.item(1, 1)])
    }

    @Test
    fun `the npcs show their bodies`() {
        val player = openGallery()

        assertEquals(listOf<Any>(PictureEncoding.NPC_BODY, "", 50), pictures(player)[GalleryWidgets.npc(3)])
    }

    @Test
    fun `each npc is named under its body`() {
        val player = openGallery()

        assertEquals("npc 50", texts(player)[GalleryWidgets.npcName(3)])
    }

    @Test
    fun `each tile shows its kind's name, icon and number`() {
        val player = openGallery()

        assertEquals("Tile 2", texts(player)[GalleryWidgets.tileLabel(1)])
        assertEquals("bank", texts(player)[GalleryWidgets.tileKind(1)])
        assertEquals(listOf<Any>(PictureEncoding.MEDIA, "staticons", 17), pictures(player)[GalleryWidgets.tilePicture(0)])
    }

    @Test
    fun `a tile past the last kind keeps its number with no name or icon`() {
        val player = openGallery()

        assertEquals("Tile 5", texts(player)[GalleryWidgets.tileLabel(4)])
        assertEquals("", texts(player)[GalleryWidgets.tileKind(4)])
        assertEquals(listOf<Any>(PictureEncoding.NONE, "", 0), pictures(player)[GalleryWidgets.tilePicture(4)])
    }

    @Test
    fun `a tile answers in the chat box with its number`() {
        val player = openGallery()

        gallery.click(player, GalleryWidgets.tileFace(2))

        assertEquals(listOf("Gallery: clicked tile 3."), TestWorld.chatbox(player))
    }

    @Test
    fun `a tile dropped on another moves there and the ones between shift`() {
        val player = openGallery()

        gallery.arrange(player, from = 0, to = 2)

        assertEquals(listOf("Tile 2", "Tile 3", "Tile 1", "Tile 4"), (0..3).map { texts(player)[GalleryWidgets.tileLabel(it)] })
        assertEquals(listOf("Gallery: moved tile 1 to place 3."), TestWorld.chatbox(player))
    }

    @Test
    fun `a moved tile answers clicks with its own number`() {
        val player = openGallery()
        gallery.arrange(player, from = 0, to = 2)

        gallery.click(player, GalleryWidgets.tileFace(2))

        assertEquals("Gallery: clicked tile 1.", TestWorld.chatbox(player).last())
    }

    @Test
    fun `a move from a place outside the tiles is ignored`() {
        val player = openGallery()

        gallery.arrange(player, from = -1, to = 0)
        gallery.arrange(player, from = GalleryWidgets.TILES, to = 0)

        assertTrue(TestWorld.chatbox(player).isEmpty())
    }

    @Test
    fun `a move to a place outside the tiles is ignored`() {
        val player = openGallery()

        gallery.arrange(player, from = 0, to = -1)
        gallery.arrange(player, from = 0, to = GalleryWidgets.TILES)

        assertTrue(TestWorld.chatbox(player).isEmpty())
    }

    @Test
    fun `a move before the gallery opened is ignored`() {
        val player = login()

        gallery.arrange(player, from = 0, to = 1)

        assertTrue(TestWorld.chatbox(player).isEmpty())
    }

    @Test
    fun `a click on a tile before the gallery opened names the place`() {
        val player = login()

        gallery.click(player, GalleryWidgets.tileFace(4))

        assertEquals(listOf("Gallery: clicked tile 5."), TestWorld.chatbox(player))
    }

    @Test
    fun `hide hides the nested layer`() {
        val player = openGallery()

        gallery.click(player, GalleryWidgets.HIDE)

        val visibility = messages(player, "WidgetVisibilityMessageWriter").single()
        assertEquals(GalleryWidgets.HIDEABLE, visibility.fields["id"])
        assertEquals(true, visibility.fields["hiddenUntilHovered"])
        assertEquals(listOf("Gallery: nested layer hidden."), TestWorld.chatbox(player))
    }

    @Test
    fun `show shows the nested layer again`() {
        val player = openGallery()

        gallery.click(player, GalleryWidgets.SHOW)

        assertEquals(false, messages(player, "WidgetVisibilityMessageWriter").single().fields["hiddenUntilHovered"])
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
        assertNull(GalleryWidgets.tile(GalleryWidgets.tilePicture(0)))
    }

    @Test
    fun `a search button opens its search`() {
        val player = login().also { searching.open(it) }

        searching.click(player, GalleryWidgets.search(0))

        assertEquals("Which tree?", messages(player, "SearchOpenMessageWriter").last().fields["title"])
    }

    @Test
    fun `a pick in a gallery search answers in the chat box`() {
        val player = login().also { searching.open(it) }
        searching.click(player, GalleryWidgets.search(0))

        SearchPrompts.pick(player, checkNotNull(player.overlays.getOverlay(SearchPrompt::class.java)).serial, index = 0)

        assertEquals(listOf("Gallery: picked Oak (oak)."), TestWorld.chatbox(player))
    }

    @Test
    fun `a search button with no search behind it does nothing`() {
        val player = login().also { searching.open(it) }

        searching.click(player, GalleryWidgets.search(1))

        assertNull(player.overlays.getOverlay(SearchPrompt::class.java))
    }

    @Test
    fun `search buttons follow each other`() {
        assertEquals(listOf(30591, 30596), listOf(GalleryWidgets.search(0), GalleryWidgets.search(GalleryWidgets.SEARCHES - 1)))
    }

    @Test
    fun `a search button names its search`() {
        assertEquals(3, GalleryWidgets.searchOf(GalleryWidgets.search(3)))
    }

    @Test
    fun `another widget names no search`() {
        assertNull(GalleryWidgets.searchOf(GalleryWidgets.CLOSE))
    }

    @Test
    fun `the name button opens the name prompt on an empty line`() {
        val player = openGallery()

        gallery.click(player, GalleryWidgets.NAME)

        assertEquals(listOf<Any>("NAME", "Name for this flow:", ""), opening(player))
    }

    @Test
    fun `the name prompt takes a saved flow's longest name`() {
        val player = openGallery()

        gallery.click(player, GalleryWidgets.NAME)

        assertEquals(SavedFlows.MAX_NAME, messages(player, "SearchOpenMessageWriter").last().fields["mostCharacters"])
    }

    @Test
    fun `a name given in the gallery answers in the chat box`() {
        val player = named("Willow chop")

        assertEquals(listOf("Gallery: named 'Willow chop'."), TestWorld.chatbox(player))
    }

    @Test
    fun `once named, the name prompt opens on that name as a saved flow's slot would`() {
        val player = named("Willow chop")

        gallery.click(player, GalleryWidgets.NAME)

        assertEquals(listOf<Any>("NAME", "Save over 'Willow chop' as:", "Willow chop"), opening(player))
    }

    @Test
    fun `reopening the gallery forgets the name`() {
        val player = named("Willow chop")
        gallery.open(player)

        gallery.click(player, GalleryWidgets.NAME)

        assertEquals(listOf<Any>("NAME", "Name for this flow:", ""), opening(player))
    }
}
