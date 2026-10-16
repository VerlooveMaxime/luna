package game.idle.flow

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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `the wilderness is Luna's surface area`() {
        assertTrue(Teleports.inWilderness(Position(3288, 3888)))
    }

    @Test
    fun `the wilderness dungeons count as the wilderness too`() {
        assertTrue(Teleports.inWilderness(Position(3000, 10000)))
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

    @Test
    fun `a town is not the wilderness`() {
        assertFalse(Teleports.inWilderness(Position(3212, 3423)))
    }
}
