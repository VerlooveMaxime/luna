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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NamePromptTest {

    private val names = mutableListOf<String>()

    private val font = ClientFont(IntArray(256) { 5 })

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(): Player = TestWorld.login("namer", Position(3200, 3200))

    /** Opens a name prompt starting on "Cows", up to 20 characters. */
    private fun name(player: Player, onName: (Player, String) -> Unit = { _, name -> names += name }) =
        SearchPrompts.openName(player, "Save over 'Cows' as:", "Cows", 20, onName)

    private fun naming(): Player = login().also { name(it) }

    private fun search(player: Player) =
        SearchPrompts.open(player, "Which tree?", listOf(StepOption("oak", "Oak", OptionIcon.Item(1521))), font) { _, _ -> }

    private fun prompt(player: Player): NamePrompt? = SearchPrompts.opened(player, NamePrompt::class.java)

    @Test
    fun `the name prompt opens in name mode with its title, starting text and most characters`() {
        val player = naming()

        assertEquals(
            mapOf("serial" to 0, "mode" to "NAME", "title" to "Save over 'Cows' as:", "emptyLine" to "", "text" to "Cows", "mostCharacters" to 20),
            TestWorld.messages(player).last().fields,
        )
    }

    @Test
    fun `the name prompt sends no rows`() {
        assertEquals("SearchOpenMessageWriter", TestWorld.messages(naming()).last().type)
    }

    @Test
    fun `the name prompt takes the input slot`() {
        assertTrue(naming().overlays.containsType(OverlayType.INPUT))
    }

    @Test
    fun `a name is handed over trimmed`() {
        val player = naming()

        SearchPrompts.name(player, serial = 0, typed = "  Willow chop! ")

        assertEquals(listOf("Willow chop!"), names)
    }

    @Test
    fun `a name closes the prompt`() {
        val player = naming()

        SearchPrompts.name(player, serial = 0, typed = "Willow chop")

        assertFalse(player.overlays.containsType(OverlayType.INPUT))
    }

    @Test
    fun `an empty line hands nothing over`() {
        val player = naming()

        SearchPrompts.name(player, serial = 0, typed = "")

        assertEquals(listOf<String>(), names)
    }

    @Test
    fun `a line of spaces hands nothing over`() {
        val player = naming()

        SearchPrompts.name(player, serial = 0, typed = "   ")

        assertEquals(listOf<String>(), names)
    }

    @Test
    fun `an empty line still closes the prompt`() {
        val player = naming()

        SearchPrompts.name(player, serial = 0, typed = " ")

        assertFalse(player.overlays.containsType(OverlayType.INPUT))
    }

    @Test
    fun `a name of the most characters is handed over`() {
        val player = naming()

        SearchPrompts.name(player, serial = 0, typed = "x".repeat(20))

        assertEquals(listOf("x".repeat(20)), names)
    }

    @Test
    fun `a name past the most characters hands nothing over`() {
        val player = naming()

        SearchPrompts.name(player, serial = 0, typed = "x".repeat(21))

        assertEquals(listOf<String>(), names)
    }

    @Test
    fun `a name meant for an earlier prompt is ignored`() {
        val player = naming()
        name(player)

        SearchPrompts.name(player, serial = 0, typed = "Willow chop")

        assertEquals(listOf<String>(), names)
    }

    @Test
    fun `a name sent to a search is ignored`() {
        val player = login()
        search(player)

        SearchPrompts.name(player, serial = 0, typed = "Willow chop")

        assertTrue(player.overlays.containsType(OverlayType.INPUT))
    }

    @Test
    fun `a pick sent to a name prompt is ignored`() {
        val player = naming()

        SearchPrompts.pick(player, serial = 0, index = 0)

        assertTrue(player.overlays.containsType(OverlayType.INPUT))
    }

    @Test
    fun `a page asked of a name prompt is ignored`() {
        val player = naming()
        val sent = TestWorld.messages(player).size

        SearchPrompts.page(player, serial = 0, offset = 0, count = 6, query = "")

        assertEquals(sent, TestWorld.messages(player).size)
    }

    @Test
    fun `closing removes the name prompt`() {
        val player = naming()

        SearchPrompts.close(player, serial = 0)

        assertFalse(player.overlays.containsType(OverlayType.INPUT))
    }

    @Test
    fun `what a name opens stays open`() {
        val player = login()
        name(player) { namer, _ -> name(namer) }

        SearchPrompts.name(player, serial = 0, typed = "Willow chop")

        assertEquals(1, prompt(player)?.serial)
    }

    @Test
    fun `names and searches share the serials`() {
        val player = login()
        search(player)

        name(player)

        assertEquals(1, prompt(player)?.serial)
    }

    @Test
    fun `an open name prompt is no search`() {
        assertNull(SearchPrompts.opened(naming(), SearchPrompt::class.java))
    }
}
