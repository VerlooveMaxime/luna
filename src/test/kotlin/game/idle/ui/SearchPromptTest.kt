package game.idle.ui

import game.idle.flow.option.OptionIcon
import game.idle.flow.option.StepOption
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.OverlayType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchPromptTest {

    private val picks = mutableListOf<String>()

    private val tree = StepOption("tree", "Tree", OptionIcon.Item(1511), note = "Woodcutting 1", level = 1)
    private val oak = StepOption("oak", "Oak", OptionIcon.Item(1521), note = "Woodcutting 15", level = 15)
    private val yew = StepOption("yew", "Yew", OptionIcon.Item(1515), note = "Woodcutting 60", blocked = "needs Woodcutting 60", level = 60)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(): Player = TestWorld.login("searcher", Position(3200, 3200))

    /** Opens a search over a greyed yew, an oak and a tree, which shows them as tree, oak, yew. */
    /** Every glyph 5 pixels wide: "Oak" is 15. */
    private val font = ClientFont(IntArray(256) { 5 })

    private fun search(player: Player, onPick: (Player, StepOption) -> Unit = { _, option -> picks += option.value }) =
        SearchPrompts.open(player, "Which tree?", listOf(yew, oak, tree), font, "Nothing to cut", onPick)

    /** Asks the open prompt for a page and returns the rows message it answers with. */
    private fun page(player: Player, offset: Int, count: Int, query: String, serial: Int = 0): Map<String, Any> {
        SearchPrompts.page(player, serial, offset, count, query)
        return fields(player, "SearchRowsMessageWriter")
    }

    private fun searching(): Player = login().also { search(it) }

    private fun prompt(player: Player): SearchPrompt? = player.overlays.getOverlay(SearchPrompt::class.java)

    private fun fields(player: Player, type: String): Map<String, Any> = TestWorld.messages(player).last { it.type == type }.fields

    @Test
    fun `opening sends the prompt, then its rows`() {
        val player = searching()

        assertEquals(listOf("SearchOpenMessageWriter", "SearchRowsMessageWriter"), TestWorld.messages(player).takeLast(2).map { it.type })
    }

    @Test
    fun `the prompt names its serial, its title and its line for an empty list`() {
        val player = searching()

        assertEquals(
            mapOf("serial" to 0, "mode" to "SEARCH", "title" to "Which tree?", "emptyLine" to "Nothing to cut", "text" to "", "mostCharacters" to 40),
            fields(player, "SearchOpenMessageWriter"),
        )
    }

    @Test
    fun `the opening rows go in the search order, greyed ones last, each with its place`() {
        val player = searching()

        assertEquals(
            "0 Tree / Woodcutting 1 / item 1511; 1 Oak / Woodcutting 15 / item 1521; 2 Yew / needs Woodcutting 60 / item 1515 / greyed",
            fields(player, "SearchRowsMessageWriter")["rows"],
        )
    }

    @Test
    fun `the opening page says what it answers, how many rows match and in what columns`() {
        val fields = fields(searching(), "SearchRowsMessageWriter")

        assertEquals(listOf<Any>(0, "", 3, 3, 0), listOf("serial", "query", "total", "columns", "offset").map { fields.getValue(it) })
    }

    @Test
    fun `the opening page holds the rows in view and one screen below`() {
        val player = login()
        SearchPrompts.open(player, "Which item?", List(40) { StepOption("$it", "Item $it", OptionIcon.Item(1)) }, font) { _, _ -> }

        assertEquals(3 * SearchPrompt.OPENING_GRID_ROWS, fields(player, "SearchRowsMessageWriter")["count"])
    }

    @Test
    fun `a page goes on from its offset`() {
        val player = searching()

        assertEquals("1 Oak / Woodcutting 15 / item 1521", page(player, offset = 1, count = 1, query = "")["rows"])
    }

    @Test
    fun `a query answers the rows whose label holds every typed word, keeping their places`() {
        val player = searching()

        assertEquals("1 Oak / Woodcutting 15 / item 1521", page(player, offset = 0, count = 10, query = "oa")["rows"])
    }

    @Test
    fun `a query's page counts only its matches`() {
        val player = searching()

        assertEquals(listOf<Any>("ye", 1), page(player, offset = 0, count = 10, query = "ye").let { listOf(it.getValue("query"), it.getValue("total")) })
    }

    @Test
    fun `scrolling a query's results keeps them`() {
        val player = searching()
        page(player, offset = 0, count = 1, query = "e")

        assertEquals("2 Yew / needs Woodcutting 60 / item 1515 / greyed", page(player, offset = 1, count = 1, query = "e")["rows"])
    }

    @Test
    fun `a page holds at most thirty rows`() {
        val player = login()
        SearchPrompts.open(player, "Which item?", List(40) { StepOption("$it", "Item $it", OptionIcon.Item(1)) }, font) { _, _ -> }

        assertEquals(SearchPrompt.MAX_PAGE, page(player, offset = 0, count = 40, query = "", serial = 0)["count"])
    }

    @Test
    fun `a page stops before overflowing the client's buffer`() {
        val player = login()
        val long = "x".repeat(200)
        SearchPrompts.open(player, "Which item?", List(30) { StepOption("$it", "$long $it", OptionIcon.Item(1)) }, font) { _, _ -> }

        assertEquals(23, page(player, offset = 0, count = 30, query = "")["count"])
    }

    @Test
    fun `a page asked from before the start is ignored`() {
        val player = searching()
        val sent = TestWorld.messages(player).size

        SearchPrompts.page(player, serial = 0, offset = -1, count = 5, query = "")

        assertEquals(sent, TestWorld.messages(player).size)
    }

    @Test
    fun `a page of no rows is ignored`() {
        val player = searching()
        val sent = TestWorld.messages(player).size

        SearchPrompts.page(player, serial = 0, offset = 0, count = 0, query = "")

        assertEquals(sent, TestWorld.messages(player).size)
    }

    @Test
    fun `a page asked for an earlier prompt is ignored`() {
        val player = searching()
        search(player)
        val sent = TestWorld.messages(player).size

        SearchPrompts.page(player, serial = 0, offset = 0, count = 5, query = "")

        assertEquals(sent, TestWorld.messages(player).size)
    }

    @Test
    fun `labels that fit a third of the chatbox show in three columns`() {
        assertEquals(3, SearchColumns.of(listOf(oak, yew), font))
    }

    @Test
    fun `a label wider than a third of the chatbox makes two columns`() {
        assertEquals(2, SearchColumns.of(listOf(oak.copy(label = "x".repeat(25))), font))
    }

    @Test
    fun `a note wider than a third of the chatbox makes two columns`() {
        assertEquals(2, SearchColumns.of(listOf(oak.copy(note = "x".repeat(25))), font))
    }

    @Test
    fun `a greyed row's reason is what must fit`() {
        assertEquals(3, SearchColumns.of(listOf(yew.copy(note = "x".repeat(25))), font))
    }

    @Test
    fun `a third of the chatbox leaves 122 pixels of text and half 199, as the client's grid`() {
        assertEquals(listOf(122, 199), listOf(SearchColumns.textRoom(3), SearchColumns.textRoom(2)))
    }

    @Test
    fun `the prompt takes the input slot`() {
        assertTrue(searching().overlays.containsType(OverlayType.INPUT))
    }

    @Test
    fun `each new prompt gets the next serial`() {
        val player = searching()

        search(player)

        assertEquals(1, prompt(player)?.serial)
    }

    @Test
    fun `serials wrap to fit the byte the client sends back`() {
        assertEquals(0, SearchPrompts.nextSerial(SearchPrompts.SERIALS - 1))
    }

    @Test
    fun `a picked row hands its option over`() {
        val player = searching()

        SearchPrompts.pick(player, serial = 0, index = 1)

        assertEquals(listOf("oak"), picks)
    }

    @Test
    fun `a pick closes the prompt`() {
        val player = searching()

        SearchPrompts.pick(player, serial = 0, index = 1)

        assertFalse(player.overlays.containsType(OverlayType.INPUT))
    }

    @Test
    fun `a greyed row cannot be picked`() {
        val player = searching()

        SearchPrompts.pick(player, serial = 0, index = 2)

        assertEquals(listOf<String>(), picks)
        assertTrue(player.overlays.containsType(OverlayType.INPUT))
    }

    @Test
    fun `a row past the list cannot be picked`() {
        val player = searching()

        SearchPrompts.pick(player, serial = 0, index = 3)

        assertEquals(listOf<String>(), picks)
    }

    @Test
    fun `a pick meant for an earlier prompt is ignored`() {
        val player = searching()
        search(player)

        SearchPrompts.pick(player, serial = 0, index = 1)

        assertEquals(listOf<String>(), picks)
    }

    @Test
    fun `a pick with no prompt open is ignored`() {
        val player = login()

        SearchPrompts.pick(player, serial = 0, index = 1)

        assertEquals(listOf<String>(), picks)
    }

    @Test
    fun `what a pick opens stays open`() {
        val player = login()
        search(player) { picker, _ -> search(picker) }

        SearchPrompts.pick(player, serial = 0, index = 1)

        assertEquals(1, prompt(player)?.serial)
    }

    @Test
    fun `closing removes the prompt`() {
        val player = searching()

        SearchPrompts.close(player, serial = 0)

        assertFalse(player.overlays.containsType(OverlayType.INPUT))
    }

    @Test
    fun `a close meant for an earlier prompt keeps the current one`() {
        val player = searching()
        search(player)

        SearchPrompts.close(player, serial = 0)

        assertEquals(1, prompt(player)?.serial)
    }

    /** The values a list holds, which a search of several shows chosen; its picks add or take them out. */
    private val list = mutableSetOf("oak")

    private fun several(player: Player) =
        SearchPrompts.openSeveral(player, "Which trees?", listOf(yew, oak, tree), font, chosen = { list.toSet() }) { _, option ->
            if (!list.remove(option.value)) list += option.value
        }

    private fun severalOpen(): Player = login().also { several(it) }

    @Test
    fun `a search of several opens in its own mode`() {
        assertEquals("SEVERAL", fields(severalOpen(), "SearchOpenMessageWriter")["mode"])
    }

    @Test
    fun `the rows chosen when it opens come first, noted chosen`() {
        assertEquals(
            "0 Oak / chosen / item 1521; 1 Tree / Woodcutting 1 / item 1511; 2 Yew / needs Woodcutting 60 / item 1515 / greyed",
            fields(severalOpen(), "SearchRowsMessageWriter")["rows"],
        )
    }

    @Test
    fun `a pick in a search of several leaves it open`() {
        val player = severalOpen()

        SearchPrompts.pick(player, serial = 0, index = 1)

        assertTrue(player.overlays.has(SearchPrompt::class.java))
    }

    @Test
    fun `a pick in a search of several sends its row again, changed, where it was`() {
        val player = severalOpen()

        SearchPrompts.pick(player, serial = 0, index = 0)

        val page = fields(player, "SearchRowsMessageWriter")
        assertEquals(listOf<Any>(0, 1, "0 Oak / Woodcutting 15 / item 1521"), listOf(page.getValue("offset"), page.getValue("count"), page.getValue("rows")))
    }

    @Test
    fun `a row picked among a query's results is sent at its place in them`() {
        val player = severalOpen()
        page(player, offset = 0, count = 6, query = "tree")

        SearchPrompts.pick(player, serial = 0, index = 1)

        val page = fields(player, "SearchRowsMessageWriter")
        assertEquals(listOf<Any>("tree", 0, "1 Tree / chosen / item 1511"), listOf(page.getValue("query"), page.getValue("offset"), page.getValue("rows")))
    }

    @Test
    fun `a row picked outside the query's results is not sent again`() {
        val player = severalOpen()
        page(player, offset = 0, count = 6, query = "tree")
        val before = TestWorld.messages(player).size

        SearchPrompts.pick(player, serial = 0, index = 0)

        assertEquals(listOf(before, false), listOf(TestWorld.messages(player).size, "oak" in list))
    }
}
