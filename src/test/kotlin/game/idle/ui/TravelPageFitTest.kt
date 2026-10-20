package game.idle.ui

import game.idle.IdleState
import game.idle.autopilot.AutopilotConfig
import game.idle.flow.ReflexKeys
import game.idle.flow.ReflexSettings
import game.idle.flow.StepSettings
import game.idle.flow.StepWords
import game.idle.flow.TeleportDestination
import game.idle.flow.Teleports
import game.idle.flow.capitalised
import game.idle.flow.option.LunaGameNames
import game.idle.font.ClientFont
import game.idle.movement.TravelChoice
import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * The Travel tab's words fit where the client draws them (web-walker movement Part 3b): names and buttons in the
 * plain font, notes and the kept-back lines in the small one, whole, over Luna's real data and a flow holding as many
 * teleport reflexes as `autopilot.jsonc` allows. Measured with the fonts of the cache, so these run on [TestWorld].
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TravelPageFitTest {

    private lateinit var small: ClientFont
    private lateinit var plain: ClientFont
    private lateinit var page: TravelPage
    private val reflexSlots = AutopilotConfig.load(AutopilotConfig.PATH).reflexSlots

    @BeforeAll
    fun load() {
        small = ClientFont.fromCache(TestWorld.context.cache)
        plain = ClientFont.fromCache(TestWorld.context.cache, ClientFont.PLAIN)
        page = TravelPage(LunaGameNames, small)
    }

    @Test
    fun `each row's name fits left of its buttons`() {
        val names = TravelChoice.entries.map { capitalised(StepWords.travelRow(it)) }

        assertEquals(emptyList<String>(), names.filter { plain.width(it) > BuilderWidgets.TRAVEL_LABEL_ROOM })
    }

    @Test
    fun `No and each Yes fit their buttons`() {
        val tooWide = (listOf(TravelPage.NO to BuilderWidgets.TRAVEL_NO_ROOM) + TravelChoice.entries.map { TravelPage.yes(it) to BuilderWidgets.TRAVEL_YES_ROOM })
            .filter { (word, room) -> plain.width(word) > room }

        assertEquals(emptyList<Pair<String, Int>>(), tooWide)
    }

    @Test
    fun `the tab's word fits its button`() {
        assertTrue(plain.width("Travel") <= BuilderWidgets.TRAVEL_TAB_ROOM)
    }

    @Test
    fun `the tolls note fits two lines whole`() {
        val note = page.note(TravelChoice.COINS)

        assertEquals(note, small.wrap(note, BuilderWidgets.TRAVEL_LINE_ROOM, lines = 2).joinToString(" "))
    }

    @Test
    fun `the spells and jewellery notes fit a line each`() {
        val notes = listOf(TravelChoice.RUNES, TravelChoice.CHARGES).map(page::note)

        assertEquals(emptyList<String>(), notes.filter { small.width(it) > BuilderWidgets.TRAVEL_LINE_ROOM })
    }

    @Test
    fun `the bank stops note fits a line`() {
        assertTrue(small.width(page.note(TravelChoice.BANK)) <= BuilderWidgets.TRAVEL_LINE_ROOM)
    }

    @Test
    fun `the always-used line fits its line`() {
        assertTrue(small.width(TravelPage.ALWAYS_USED) <= BuilderWidgets.TRAVEL_ALWAYS_ROOM)
    }

    @Test
    fun `the spells kept back for every reflex a flow holds fit under their note, whole`() {
        val lines = page.rows(flowOf(mostRunes()), worn = emptySet())[TravelChoice.RUNES.ordinal].lines

        assertEquals(listOf(3, false), listOf(lines.size, lines.any { it.endsWith("..") }))
    }

    @Test
    fun `the jewellery kept back for every reflex a flow holds fits under its note, whole`() {
        val lines = page.rows(flowOf(mostJewellery()), worn = emptySet())[TravelChoice.CHARGES.ordinal].lines

        assertEquals(listOf(3, false), listOf(lines.size, lines.any { it.endsWith("..") }))
    }

    /** A flow whose one step uses a teleport reflex to each of [destinations]. */
    private fun flowOf(destinations: List<TeleportDestination>): IdleState {
        val reflexes = destinations.mapIndexed { index, destination ->
            ReflexSettings(index + 1, mapOf(ReflexKeys.DO to ReflexKeys.TELEPORT, ReflexKeys.DESTINATION to Teleports.key(destination)))
        }
        return IdleState(steps = listOf(StepSettings("walk", reflexes = reflexes.map { it.id })), reflexes = reflexes)
    }

    /** As many spells as a flow holds reflexes, each picked for the most items the ones before it do not list. */
    private fun mostRunes(): List<TeleportDestination> {
        val spells = Teleports.all.filterIsInstance<TeleportDestination.Spell>()
        fun items(spell: TeleportDestination.Spell) = Teleports.listed(spell.spell.requirements, emptySet()).keys
        return (1..reflexSlots).fold(listOf<TeleportDestination.Spell>()) { picked, _ ->
            picked + (spells - picked.toSet()).maxBy { spell -> (items(spell) - picked.flatMap(::items).toSet()).size }
        }
    }

    /** As many jewellery destinations as a flow holds reflexes, a piece of each kind first. */
    private fun mostJewellery(): List<TeleportDestination> {
        val jewellery = Teleports.all.filterIsInstance<TeleportDestination.Jewellery>()
        return (jewellery.distinctBy { it.jewellery } + jewellery).distinct().take(reflexSlots)
    }
}
