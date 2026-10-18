package game.idle.flow

import api.bot.webwalk.model.TeleportDefinition
import api.bot.webwalk.model.TeleportKind
import game.idle.flow.option.FakeNames
import game.item.degradable.jewellery.TeleportJewellery
import game.skill.magic.EquipmentRequirement
import game.skill.magic.ItemRequirement
import game.skill.magic.Rune
import game.skill.magic.RuneRequirement
import game.skill.magic.SpellRequirement
import game.skill.magic.teleportSpells.TeleportSpell
import io.luna.game.model.Position
import io.luna.game.model.mob.Spellbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TeleportsTest {

    private val glory = 1712
    private val duelingRing = 2552
    private val gamesNecklace = 3853
    private val banana = 1963
    private val names = FakeNames(mapOf(glory to "Amulet of glory(4)", duelingRing to "Ring of dueling(8)", gamesNecklace to "Games necklace(8)", banana to "Banana"))
    private val varrock = TeleportDestination.Spell(TeleportSpell.VARROCK)
    private val watchtower = TeleportDestination.Spell(TeleportSpell.WATCHTOWER)
    private val paddewwa = TeleportDestination.Spell(TeleportSpell.PADDEWWA)
    private val edgeville = TeleportDestination.Jewellery(TeleportJewellery.AMULET_OF_GLORY, 0)
    private val castleWars = TeleportDestination.Jewellery(TeleportJewellery.DUELING_RING, 1)

    @Test
    fun `the spells outside the wilderness come first, then every jewellery destination`() {
        val spells = Teleports.all.filterIsInstance<TeleportDestination.Spell>().map { it.spell }
        val jewellery = Teleports.all.filterIsInstance<TeleportDestination.Jewellery>().map { it.jewellery to it.index }

        assertEquals(
            listOf(TeleportSpell.VARROCK, TeleportSpell.LUMBRIDGE, TeleportSpell.FALADOR, TeleportSpell.CAMELOT, TeleportSpell.ARDOUGNE, TeleportSpell.PADDEWWA, TeleportSpell.WATCHTOWER, TeleportSpell.SENNTISTEN, TeleportSpell.TROLLHEIM, TeleportSpell.APE_ATOLL, TeleportSpell.KHARYRLL, TeleportSpell.LASSAR),
            spells,
        )
        assertEquals(
            listOf(TeleportJewellery.GAMES_NECKLACE to 0, TeleportJewellery.DUELING_RING to 0, TeleportJewellery.DUELING_RING to 1) + (0..3).map { TeleportJewellery.AMULET_OF_GLORY to it },
            jewellery,
        )
    }

    @Test
    fun `the wilderness spells are never offered`() {
        val offered = Teleports.all.filterIsInstance<TeleportDestination.Spell>().map { it.spell }

        assertEquals(emptyList<TeleportSpell>(), listOf(TeleportSpell.DAREEYAK, TeleportSpell.CARRALLANGER, TeleportSpell.ANNAKARL, TeleportSpell.GHORROCK).filter { it in offered })
    }

    @Test
    fun `a spell is keyed by its name, a jewellery destination by the jewellery and the place`() {
        assertEquals(listOf("varrock", "amulet_of_glory:edgeville", "dueling_ring:castle_wars_arena"), listOf(varrock, edgeville, castleWars).map(Teleports::key))
    }

    @Test
    fun `every destination is found again by its key`() {
        assertEquals(Teleports.all, Teleports.all.map { Teleports.of(Teleports.key(it)) })
    }

    @Test
    fun `a key no teleport has finds none`() {
        assertNull(Teleports.of("dareeyak"))
    }

    @Test
    fun `a place is named as Luna names it`() {
        assertEquals(listOf("Varrock", "the Watchtower", "Edgeville", "Castle Wars Arena"), listOf(varrock, watchtower, edgeville, castleWars).map(Teleports::named))
    }

    @Test
    fun `a row labels a spell in sentence case and a jewellery destination with its jewellery`() {
        assertEquals(listOf("The Watchtower", "Edgeville (amulet of glory)", "Castle Wars Arena (ring of dueling)"), listOf(watchtower, edgeville, castleWars).map { Teleports.label(it, names) })
    }

    @Test
    fun `a jewellery is named without its charges`() {
        assertEquals("games necklace", Teleports.jewelleryName(TeleportJewellery.GAMES_NECKLACE, names))
    }

    @Test
    fun `a spell is pictured by its spellbook's lit icon, a jewellery destination by the jewellery`() {
        assertEquals(
            listOf(StepIcon.Media("magicon", 12), StepIcon.Media("magicon2", 22), StepIcon.Item(glory)),
            listOf(varrock, paddewwa, edgeville).map(Teleports::picture),
        )
    }

    @Test
    fun `a spell's cost lists its runes`() {
        assertEquals("3 air, 1 fire, 1 law", Teleports.cost(TeleportSpell.VARROCK, names))
    }

    @Test
    fun `an ancient spell's cost names its spellbook first`() {
        assertEquals("Ancient spellbook, 1 air, 1 fire, 2 law", Teleports.cost(TeleportSpell.PADDEWWA, names))
    }

    @Test
    fun `an item a spell takes is part of its cost`() {
        assertEquals("2 water, 2 fire, 2 law, 1 banana", Teleports.cost(TeleportSpell.APE_ATOLL, names))
    }

    @Test
    fun `a rune or an item is worded in the cost, equipment or a kind Luna's rules do not name is not`() {
        assertEquals(
            listOf("3 air", "1 banana", null, null),
            listOf(RuneRequirement(Rune.AIR, 3), ItemRequirement(banana), EquipmentRequirement(2415), object : SpellRequirement {}).map { Teleports.costOf(it, names) },
        )
    }

    @Test
    fun `a worn item that is no staff stands for no rune`() {
        val darklight = 6746

        assertEquals(3, Teleports.needs(varrock, worn = setOf(darklight), names).size)
    }

    @Test
    fun `a spell needs each of its runes carried, a combination rune counting`() {
        assertEquals(
            listOf(
                ToolNeed("air rune", mapOf(556 to 0, 4695 to 0, 4696 to 0, 4697 to 0), countable = false),
                ToolNeed("fire rune", mapOf(554 to 0, 4697 to 0, 4694 to 0, 4699 to 0), countable = false),
                ToolNeed("law rune", mapOf(563 to 0), countable = false),
            ),
            Teleports.needs(varrock, worn = emptySet(), names),
        )
    }

    @Test
    fun `a worn staff stands for its runes`() {
        val staffOfAir = 1381

        assertEquals(listOf("fire rune", "law rune"), Teleports.needs(varrock, worn = setOf(staffOfAir), names).map { it.word })
    }

    @Test
    fun `a staff carried but not worn stands for nothing`() {
        assertEquals(3, Teleports.needs(varrock, worn = emptySet(), names).size)
    }

    @Test
    fun `an item a spell takes is needed one by one`() {
        assertEquals(ToolNeed(null, mapOf(banana to 0)), Teleports.needs(TeleportDestination.Spell(TeleportSpell.APE_ATOLL), emptySet(), names).last())
    }

    @Test
    fun `equipment a spell takes is needed worn`() {
        assertEquals(ToolNeed(null, mapOf(2415 to 0), worn = true), Teleports.need(EquipmentRequirement(2415), emptySet()))
    }

    @Test
    fun `a requirement of a kind Luna's rules do not name needs nothing`() {
        assertNull(Teleports.need(object : SpellRequirement {}, emptySet()))
    }

    @Test
    fun `a rune requirement needs its rune`() {
        assertEquals("law rune", Teleports.need(RuneRequirement(Rune.LAW, 1), emptySet())?.word)
    }

    @Test
    fun `an item requirement needs its item`() {
        assertEquals(ToolNeed(null, mapOf(banana to 0)), Teleports.need(ItemRequirement(banana), emptySet()))
    }

    @Test
    fun `a jewellery destination needs a charged piece of it`() {
        assertEquals(
            listOf(ToolNeed("amulet of glory", mapOf(1712 to 0, 1710 to 0, 1708 to 0, 1706 to 0))),
            Teleports.needs(edgeville, worn = emptySet(), names),
        )
    }

    @Test
    fun `a spell leaves the wilderness as deep as Luna's default, jewellery as deep as its own rule`() {
        assertEquals(listOf(20, 30, 20), listOf(varrock, edgeville, castleWars).map(Teleports::wildernessLimit))
    }

    @Test
    fun `the spellbooks are named as the warning says them`() {
        assertEquals(listOf("regular", "Ancient"), listOf(Spellbook.REGULAR, Spellbook.ANCIENT).map(Teleports::bookName))
    }

    @Test
    fun `a teleport lands at its spell's destination or around its jewellery's centre`() {
        assertEquals(listOf(Position(3212, 3423), Position(3087, 3496)), listOf(varrock, edgeville).map(Teleports::lands))
    }

    @Test
    fun `in a sentence a spell keeps Luna's article and a jewellery destination its jewellery`() {
        assertEquals(listOf("the Watchtower", "Edgeville (amulet of glory)"), listOf(watchtower, edgeville).map { Teleports.inSentence(it, names) })
    }

    @Test
    fun `Kharyrll is spelt as the place is`() {
        assertEquals("Kharyrll", Teleports.named(TeleportDestination.Spell(TeleportSpell.KHARYRLL)))
    }

    private fun webTeleport(kind: TeleportKind, key: String?, option: Int? = null) =
        TeleportDefinition("teleport", kind, key, option, Position(3200, 3200), "node", cost = 5)

    @Test
    fun `a spell of the travel web is the reflexes' spell`() {
        assertEquals(varrock, Teleports.of(webTeleport(TeleportKind.SPELL, "VARROCK")))
    }

    @Test
    fun `a jewellery destination of the travel web is its menu's, counted from 1`() {
        assertEquals(castleWars, Teleports.of(webTeleport(TeleportKind.JEWELLERY, "DUELING_RING", option = 2)))
    }

    @Test
    fun `the home teleport is no reflex's`() {
        assertNull(Teleports.of(webTeleport(TeleportKind.HOME, key = null)))
    }

    @Test
    fun `a spell landing in the wilderness is no reflex's`() {
        assertNull(Teleports.of(webTeleport(TeleportKind.SPELL, "ANNAKARL")))
    }

    @Test
    fun `a spell Luna does not have is none`() {
        assertNull(Teleports.of(webTeleport(TeleportKind.SPELL, "ZANARIS")))
    }

    @Test
    fun `jewellery Luna does not have is none`() {
        assertNull(Teleports.of(webTeleport(TeleportKind.JEWELLERY, "SKILLS_NECKLACE", option = 1)))
    }

    @Test
    fun `jewellery without a destination is none`() {
        assertNull(Teleports.of(webTeleport(TeleportKind.JEWELLERY, "AMULET_OF_GLORY", option = null)))
    }

    private fun reflex(id: Int, vararg values: Pair<String, String>) = ReflexSettings(id, mapOf(*values))

    @Test
    fun `a trip keeps back the teleports of the reflexes attached to a step, once each`() {
        val steps = listOf(StepSettings("chop", reflexes = listOf(1, 2, 3)), StepSettings("drop", reflexes = listOf(4)))
        val reflexes = listOf(
            reflex(1, "do" to "teleport", "destination" to "varrock"),
            reflex(2, "do" to "eat"),
            reflex(3, "do" to "teleport", "destination" to "amulet_of_glory:edgeville"),
            reflex(4, "do" to "teleport", "destination" to "varrock"),
            reflex(5, "do" to "teleport", "destination" to "falador"),
        )

        assertEquals(listOf(varrock, edgeville), Teleports.reserved(steps, reflexes))
    }

    @Test
    fun `each reflex keeping a teleport back is known by its place among the flow's reflexes`() {
        val steps = listOf(StepSettings("chop", reflexes = listOf(1, 3, 4)))
        val reflexes = listOf(
            reflex(1, "do" to "teleport", "destination" to "varrock"),
            reflex(2, "do" to "teleport", "destination" to "falador"),
            reflex(3, "do" to "eat"),
            reflex(4, "do" to "teleport", "destination" to "varrock"),
        )

        assertEquals(mapOf(0 to varrock, 3 to varrock), Teleports.reserving(steps, reflexes))
    }

    @Test
    fun `amounts name a rune by its kind and anything else by its name`() {
        assertEquals("3 air, 1 banana", Teleports.amounts(mapOf(Rune.AIR.id to 3, banana to 1), FakeNames(mapOf(Rune.AIR.id to "Air rune", banana to "Banana"))))
    }

    @Test
    fun `a teleport reflex going nowhere it can keeps nothing back`() {
        val steps = listOf(StepSettings("chop", reflexes = listOf(1, 2)))

        assertEquals(emptyList<TeleportDestination>(), Teleports.reserved(steps, listOf(reflex(1, "do" to "teleport"), reflex(2, "do" to "teleport", "destination" to "dareeyak"))))
    }

    private val law = Rune.LAW.id
    private val air = Rune.AIR.id
    private val fire = Rune.FIRE.id
    private val smoke = 4697

    @Test
    fun `what a cast used up reads in the order the spell lists it, a combination rune with its first rune`() {
        val requirements = listOf(RuneRequirement(Rune.AIR, 3), RuneRequirement(Rune.FIRE, 1), RuneRequirement(Rune.LAW, 1))

        assertEquals(listOf(air to 2, smoke to 1, law to 1), Teleports.inSpellOrder(requirements, mapOf(law to 1, air to 2, smoke to 1)).toList())
    }

    @Test
    fun `items a cast used up follow its runes as the spell lists them, anything it does not list last`() {
        val requirements = listOf(RuneRequirement(Rune.FIRE, 2), ItemRequirement(banana), EquipmentRequirement(2415))

        assertEquals(listOf(fire to 2, banana to 1, 995 to 1), Teleports.inSpellOrder(requirements, mapOf(995 to 1, banana to 1, fire to 2)).toList())
    }

    @Test
    fun `what a spell lists is each rune no worn staff gives and each item`() {
        val requirements = listOf(RuneRequirement(Rune.AIR, 3), RuneRequirement(Rune.LAW, 1), ItemRequirement(banana, 2), EquipmentRequirement(2415))
        val staffOfAir = 1381

        assertEquals(mapOf(law to 1, banana to 2), Teleports.listed(requirements, worn = setOf(staffOfAir)))
    }
}
