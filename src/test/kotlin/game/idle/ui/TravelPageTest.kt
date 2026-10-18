package game.idle.ui

import game.content.sailing.ShipRoute
import game.idle.IdleState
import game.idle.flow.ReflexKeys
import game.idle.flow.ReflexSettings
import game.idle.flow.StepSettings
import game.idle.flow.option.FakeNames
import game.idle.font.ClientFont
import game.idle.movement.TravelAllowance
import game.idle.movement.TravelChoice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TravelPageTest {

    private val names = FakeNames(
        mapOf(556 to "Air rune", 557 to "Earth rune", 554 to "Fire rune", 555 to "Water rune", 563 to "Law rune", 1963 to "Banana", 1712 to "Amulet of glory(4)", 2552 to "Ring of dueling(8)"),
    )

    /** Every glyph 1 px wide, so no line wraps. */
    private val narrow = TravelPage(names, ClientFont(IntArray(256) { 1 }))

    /** Every glyph 5 px wide: 92 of them fill a line. */
    private val wide = TravelPage(names, ClientFont(IntArray(256) { 5 }))

    private val staffOfAir = 1381

    private fun teleport(id: Int, destination: String) = ReflexSettings(id, mapOf(ReflexKeys.DO to ReflexKeys.TELEPORT, ReflexKeys.DESTINATION to destination))

    /** A flow whose one step uses every reflex in [reflexes]. */
    private fun flow(vararg reflexes: ReflexSettings) = IdleState(steps = listOf(StepSettings("chop", reflexes = reflexes.map { it.id })), reflexes = reflexes.toList())

    private fun lines(state: IdleState, choice: TravelChoice, worn: Set<Int> = emptySet(), page: TravelPage = narrow): List<String> =
        page.rows(state, worn)[choice.ordinal].lines

    @Test
    fun `a row per Travel choice, tolls first`() {
        assertEquals(listOf(TravelChoice.COINS, TravelChoice.RUNES, TravelChoice.CHARGES), narrow.rows(IdleState(), emptySet()).map { it.choice })
    }

    @Test
    fun `a new flow allows nothing`() {
        assertEquals(listOf(false, false, false), narrow.rows(IdleState(), emptySet()).map { it.allowed })
    }

    @Test
    fun `a choice the flow allows shows allowed`() {
        assertEquals(listOf(false, true, false), narrow.rows(IdleState(travel = TravelAllowance(runes = true)), emptySet()).map { it.allowed })
    }

    @Test
    fun `tolls and fares name the gate and each pair of ports a paid ship sails between`() {
        assertEquals(
            "Al Kharid toll gate 10 coins; ships between Port Sarim and Karamja, Ardougne and Brimhaven 30 coins",
            narrow.note(TravelChoice.COINS),
        )
    }

    @Test
    fun `free ships cost no fare and are not named`() {
        val page = TravelPage(names, ClientFont(IntArray(256) { 1 }), ships = listOf(ShipRoute.PORT_SARIM_TO_ENTRANA, ShipRoute.ENTRANA_TO_PORT_SARIM))

        assertEquals("Al Kharid toll gate 10 coins", page.note(TravelChoice.COINS))
    }

    @Test
    fun `the toll is Luna's`() {
        assertEquals("Al Kharid toll gate 25 coins", TravelPage(names, ClientFont(IntArray(256)), toll = 25, ships = emptyList()).note(TravelChoice.COINS))
    }

    @Test
    fun `a paid ship whose way back is not listed is named from the port nearest its departure`() {
        val page = TravelPage(names, ClientFont(IntArray(256)), ships = listOf(ShipRoute.PORT_SARIM_TO_KARAMJA, ShipRoute.ENTRANA_TO_PORT_SARIM))

        assertEquals("Al Kharid toll gate 10 coins; ships between Port Sarim and Karamja 30 coins", page.note(TravelChoice.COINS))
    }

    @Test
    fun `spells count the teleport spells a trip may cast`() {
        assertEquals("12 spells, with the runes carried", narrow.note(TravelChoice.RUNES))
    }

    @Test
    fun `jewellery counts its destinations`() {
        assertEquals("7 destinations, with jewellery carried", narrow.note(TravelChoice.CHARGES))
    }

    @Test
    fun `a note is grey`() {
        assertEquals(listOf("@gry@12 spells, with the runes carried"), lines(IdleState(), TravelChoice.RUNES))
    }

    @Test
    fun `a note too long for a line goes on two`() {
        assertEquals(
            listOf("@gry@Al Kharid toll gate 10 coins; ships between Port Sarim and Karamja, Ardougne and Brimhaven", "@gry@30 coins"),
            lines(IdleState(), TravelChoice.COINS, page = wide),
        )
    }

    @Test
    fun `a spell reflex keeps back its runes under the spells, in white`() {
        assertEquals(
            listOf("@gry@12 spells, with the runes carried", "@whi@Kept for reflex 1's Varrock teleport: 3 air, 1 fire, 1 law"),
            lines(flow(teleport(4, "varrock")), TravelChoice.RUNES),
        )
    }

    @Test
    fun `a reflex is named by its place in the flow`() {
        val state = flow(ReflexSettings(1), teleport(2, "varrock"))

        assertEquals("@whi@Kept for reflex 2's Varrock teleport: 3 air, 1 fire, 1 law", lines(state, TravelChoice.RUNES).last())
    }

    @Test
    fun `a worn staff's runes are not kept back`() {
        assertEquals("@whi@Kept for reflex 1's Varrock teleport: 1 fire, 1 law", lines(flow(teleport(1, "varrock")), TravelChoice.RUNES, worn = setOf(staffOfAir)).last())
    }

    @Test
    fun `a spell needing an item keeps it back too`() {
        assertEquals("@whi@Kept for reflex 1's Ape Atoll teleport: 2 water, 2 fire, 2 law, 1 banana", lines(flow(teleport(1, "ape_atoll")), TravelChoice.RUNES).last())
    }

    @Test
    fun `a place Luna names with an article drops it before teleport`() {
        assertEquals("@whi@Kept for reflex 1's Watchtower teleport: 2 earth, 2 law", lines(flow(teleport(1, "watchtower")), TravelChoice.RUNES).last())
    }

    @Test
    fun `several spell reflexes keep back their runes summed on one line`() {
        val state = flow(teleport(1, "varrock"), teleport(2, "falador"))

        assertEquals("@whi@Kept for reflexes 1 and 2's teleports: 6 air, 1 fire, 2 law, 1 water", lines(state, TravelChoice.RUNES).last())
    }

    @Test
    fun `three reflexes are listed with commas`() {
        val state = flow(teleport(1, "varrock"), teleport(2, "falador"), teleport(3, "lumbridge"))

        assertEquals("@whi@Kept for reflexes 1, 2 and 3's teleports:", lines(state, TravelChoice.RUNES).last().substringBefore(" 9"))
    }

    @Test
    fun `reflexes teleporting to one place keep back one cast`() {
        val state = flow(teleport(1, "varrock"), teleport(2, "varrock"))

        assertEquals("@whi@Kept for reflexes 1 and 2's Varrock teleport: 3 air, 1 fire, 1 law", lines(state, TravelChoice.RUNES).last())
    }

    @Test
    fun `a jewellery reflex keeps back a charge under the jewellery`() {
        assertEquals(
            listOf("@gry@7 destinations, with jewellery carried", "@whi@Kept for reflex 1's Edgeville teleport: 1 amulet of glory charge"),
            lines(flow(teleport(1, "amulet_of_glory:edgeville")), TravelChoice.CHARGES),
        )
    }

    @Test
    fun `two places of one piece keep back two of its charges`() {
        val state = flow(teleport(1, "amulet_of_glory:edgeville"), teleport(2, "amulet_of_glory:karamja"))

        assertEquals("@whi@Kept for reflexes 1 and 2's teleports: 2 amulet of glory charges", lines(state, TravelChoice.CHARGES).last())
    }

    @Test
    fun `pieces of two kinds are kept back each`() {
        val state = flow(teleport(1, "amulet_of_glory:edgeville"), teleport(2, "dueling_ring:castle_wars_arena"))

        assertEquals("@whi@Kept for reflexes 1 and 2's teleports: 1 amulet of glory charge, 1 ring of dueling charge", lines(state, TravelChoice.CHARGES).last())
    }

    @Test
    fun `a spell reflex keeps nothing back from the jewellery, nor a jewellery reflex from the spells`() {
        val state = flow(teleport(1, "varrock"), teleport(2, "amulet_of_glory:edgeville"))

        assertEquals(
            listOf("@whi@Kept for reflex 1's Varrock teleport: 3 air, 1 fire, 1 law", "@whi@Kept for reflex 2's Edgeville teleport: 1 amulet of glory charge"),
            listOf(lines(state, TravelChoice.RUNES).last(), lines(state, TravelChoice.CHARGES).last()),
        )
    }

    @Test
    fun `tolls and fares keep nothing back`() {
        assertEquals(1, lines(flow(teleport(1, "varrock")), TravelChoice.COINS).size)
    }

    @Test
    fun `a teleport reflex no step uses keeps nothing back`() {
        val state = IdleState(steps = listOf(StepSettings("chop")), reflexes = listOf(teleport(1, "varrock")))

        assertEquals(listOf("@gry@12 spells, with the runes carried"), lines(state, TravelChoice.RUNES))
    }

    @Test
    fun `a row shows at most three lines`() {
        val state = flow(teleport(1, "varrock"), teleport(2, "falador"), teleport(3, "lumbridge"), teleport(4, "camelot"))
        val tiny = TravelPage(names, ClientFont(IntArray(256) { 20 }))

        assertEquals(BuilderWidgets.TRAVEL_LINES, lines(state, TravelChoice.RUNES, page = tiny).size)
    }

    private fun updates(state: IdleState): Map<Int, WidgetUpdate> = narrow.updates(state, emptySet()).associateBy {
        when (it) {
            is WidgetUpdate.Text -> it.id
            is WidgetUpdate.Colour -> it.id
            else -> error("no other update")
        }
    }

    @Test
    fun `each row is named as the long-way line names it, with a capital`() {
        val sent = updates(IdleState())

        assertEquals(
            listOf("Tolls and fares", "Teleport spells", "Teleport jewellery"),
            (0 until BuilderWidgets.TRAVEL_ROWS).map { (sent.getValue(BuilderWidgets.travelLabel(it)) as WidgetUpdate.Text).text },
        )
    }

    @Test
    fun `No is lit until the flow allows the choice`() {
        val sent = updates(IdleState())

        assertEquals(
            listOf(
                WidgetUpdate.Text(BuilderWidgets.travelNo(0), "@whi@No"),
                WidgetUpdate.Colour(BuilderWidgets.travelNoFrame(0), BuilderWidgets.LIT),
                WidgetUpdate.Text(BuilderWidgets.travelYes(0), "Pay coins"),
                WidgetUpdate.Colour(BuilderWidgets.travelYesFrame(0), BuilderWidgets.UNLIT),
            ),
            listOf(sent[BuilderWidgets.travelNo(0)], sent[BuilderWidgets.travelNoFrame(0)], sent[BuilderWidgets.travelYes(0)], sent[BuilderWidgets.travelYesFrame(0)]),
        )
    }

    @Test
    fun `an allowed choice lights its Yes`() {
        val sent = updates(IdleState(travel = TravelAllowance(charges = true)))

        assertEquals(
            listOf(
                WidgetUpdate.Text(BuilderWidgets.travelNo(2), "No"),
                WidgetUpdate.Colour(BuilderWidgets.travelNoFrame(2), BuilderWidgets.UNLIT),
                WidgetUpdate.Text(BuilderWidgets.travelYes(2), "@whi@Use charges"),
                WidgetUpdate.Colour(BuilderWidgets.travelYesFrame(2), BuilderWidgets.LIT),
            ),
            listOf(sent[BuilderWidgets.travelNo(2)], sent[BuilderWidgets.travelNoFrame(2)], sent[BuilderWidgets.travelYes(2)], sent[BuilderWidgets.travelYesFrame(2)]),
        )
    }

    @Test
    fun `each choice's Yes says what it spends`() {
        assertEquals(listOf("Pay coins", "Use runes", "Use charges"), TravelChoice.entries.map(TravelPage::yes))
    }

    @Test
    fun `a row's lines are filled and the ones past them emptied`() {
        val sent = updates(IdleState())

        assertEquals(
            listOf(WidgetUpdate.Text(BuilderWidgets.travelLine(1, 0), "@gry@12 spells, with the runes carried"), WidgetUpdate.Text(BuilderWidgets.travelLine(1, 1), "")),
            listOf(sent[BuilderWidgets.travelLine(1, 0)], sent[BuilderWidgets.travelLine(1, 1)]),
        )
    }

    @Test
    fun `the page ends with what is always used, in grey`() {
        assertEquals(
            WidgetUpdate.Text(BuilderWidgets.TRAVEL_ALWAYS, "@gry@Doors, gates, stairs, ladders, trapdoors and free ships are always used."),
            narrow.updates(IdleState(), emptySet()).last(),
        )
    }
}
