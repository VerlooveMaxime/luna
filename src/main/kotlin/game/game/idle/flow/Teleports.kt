package game.idle.flow

import game.idle.flow.option.GameNames
import game.idle.movement.inWilderness
import game.item.degradable.jewellery.TeleportJewellery
import game.item.degradable.jewellery.TeleportJewellery.JewelleryDestination
import game.skill.magic.CombinationRune
import game.skill.magic.EquipmentRequirement
import game.skill.magic.ItemRequirement
import game.skill.magic.Rune
import game.skill.magic.RuneRequirement
import game.skill.magic.SpellRequirement
import game.skill.magic.Staff
import game.skill.magic.teleportSpells.TeleportAction
import game.skill.magic.teleportSpells.TeleportSpell
import io.luna.game.model.Position
import io.luna.game.model.mob.Spellbook

/** Where a reflex teleports (S13 part 5): by one of Luna's teleport spells, or by a piece of its teleport jewellery. */
sealed interface TeleportDestination {

    data class Spell(val spell: TeleportSpell) : TeleportDestination

    /** The [index]th destination of [jewellery]'s menu. */
    data class Jewellery(val jewellery: TeleportJewellery, val index: Int) : TeleportDestination {

        val destination: JewelleryDestination get() = jewellery.destinations[index]
    }
}

/**
 * Luna's teleports as a reflex offers, keys, names and pictures them (S13 part 5, Maxime 2026-10-16): every teleport
 * spell whose destination lies outside Luna's wilderness (no automation enters it unless the player said so), then
 * every destination of its teleport jewellery. Kept by a key of the spell's or the jewellery's enum name; named by the
 * spell's own name or the menu's option; pictured with the spellbook's lit icon or the jewellery's picture.
 */
object Teleports {

    /** What the list offers, spells first in Luna's order, then the jewellery's destinations in menu order, none in the wilderness. */
    val all: List<TeleportDestination> by lazy {
        val spells = TeleportSpell.VALUES.map(TeleportDestination::Spell)
        val jewellery = TeleportJewellery.entries.flatMap { jewellery -> jewellery.destinations.indices.map { TeleportDestination.Jewellery(jewellery, it) } }
        (spells + jewellery).filter { !inWilderness(lands(it)) }
    }

    /** Where the teleport lands, a jewellery's around its centre. */
    fun lands(destination: TeleportDestination): Position =
        when (destination) {
            is TeleportDestination.Spell -> destination.spell.destination
            is TeleportDestination.Jewellery -> destination.destination.centre
        }

    /** The destination kept under [key], null for none offered. */
    fun of(key: String): TeleportDestination? = all.firstOrNull { key(it) == key }

    /** "varrock", "amulet_of_glory:edgeville". */
    fun key(destination: TeleportDestination): String =
        when (destination) {
            is TeleportDestination.Spell -> destination.spell.name.lowercase()
            is TeleportDestination.Jewellery -> "${destination.jewellery.name.lowercase()}:${named(destination).lowercase().replace(' ', '_')}"
        }

    /** The place as Luna names it: "Varrock", "the Watchtower", "Edgeville", "Al Kharid Duel Arena". */
    fun named(destination: TeleportDestination): String =
        when (destination) {
            is TeleportDestination.Spell -> destination.spell.displayName
            is TeleportDestination.Jewellery -> destination.destination.option.removeSuffix(".")
        }

    /** The list's row: "Varrock", "The Watchtower", "Edgeville (amulet of glory)". */
    fun label(destination: TeleportDestination, names: GameNames): String =
        when (destination) {
            is TeleportDestination.Spell -> capitalised(named(destination))
            is TeleportDestination.Jewellery -> inSentence(destination, names)
        }

    /** The reflex's sentence, mid-sentence: "teleport Varrock", "teleport the Watchtower", "teleport Edgeville (amulet of glory)". */
    fun inSentence(destination: TeleportDestination, names: GameNames): String =
        when (destination) {
            is TeleportDestination.Spell -> named(destination)
            is TeleportDestination.Jewellery -> "${named(destination)} (${jewelleryName(destination.jewellery, names)})"
        }

    /** "amulet of glory", "ring of dueling": the item's name without its charges. */
    fun jewelleryName(jewellery: TeleportJewellery, names: GameNames): String =
        names.item(jewellery.items.first()).replace(CHARGES, "").trim().lowercase()

    fun picture(destination: TeleportDestination): StepIcon =
        when (destination) {
            is TeleportDestination.Spell -> PICTURES.getValue(destination.spell)
            is TeleportDestination.Jewellery -> StepIcon.Item(destination.jewellery.items.first())
        }

    /** What a spell costs, as its row notes it: "3 air, 1 fire, 1 law", "Ancient spellbook, 1 air, 1 fire, 2 law", "..., 1 banana". */
    fun cost(spell: TeleportSpell, names: GameNames): String {
        val book = if (spell.style.spellbook == Spellbook.ANCIENT) listOf("Ancient spellbook") else emptyList()
        return (book + spell.requirements.mapNotNull { costOf(it, names) }).joinToString(", ")
    }

    /** One requirement as the note words it, null for one Luna's rules do not name: "3 air", "1 banana". */
    fun costOf(requirement: SpellRequirement, names: GameNames): String? =
        when (requirement) {
            is RuneRequirement -> "${requirement.amount} ${requirement.rune.name.lowercase()}"
            is ItemRequirement -> "${requirement.amount} ${names.item(requirement.id).lowercase()}"
            else -> null
        }

    /**
     * What [destination] needs carried: each rune a worn staff does not stand for (a combination rune counts), the
     * spell's items, or a charged piece of the jewellery; the configure screen warns about what the flow does not supply.
     */
    fun needs(destination: TeleportDestination, worn: Set<Int>, names: GameNames): List<ToolNeed> =
        when (destination) {
            is TeleportDestination.Spell -> destination.spell.requirements.mapNotNull { need(it, worn) }
            is TeleportDestination.Jewellery ->
                listOf(ToolNeed(jewelleryName(destination.jewellery, names), destination.jewellery.items.associateWith { 0 }))
        }

    /** What one [requirement] of a spell needs carried, null when [worn] covers it or Luna's rules know no such need. */
    fun need(requirement: SpellRequirement, worn: Set<Int>): ToolNeed? =
        when (requirement) {
            is RuneRequirement -> runeNeed(requirement.rune, worn)
            is ItemRequirement -> ToolNeed(null, mapOf(requirement.id to 0))
            is EquipmentRequirement -> ToolNeed(null, mapOf(requirement.id to 0), worn = true)
            else -> null
        }

    private fun runeNeed(rune: Rune, worn: Set<Int>): ToolNeed? {
        if (worn.any { Staff.ID_TO_STAFF[it]?.represents?.contains(rune) == true }) return null
        val items = listOf(rune.id) + CombinationRune.entries.filter { rune in it.represents }.map { it.id }
        return ToolNeed("${rune.name.lowercase()} rune", items.associateWith { 0 }, countable = false)
    }

    /** The deepest wilderness level Luna lets [destination]'s teleport leave from. */
    fun wildernessLimit(destination: TeleportDestination): Int =
        when (destination) {
            is TeleportDestination.Spell -> TeleportAction.MAX_WILDERNESS_LEVEL
            is TeleportDestination.Jewellery -> destination.jewellery.maxWildernessLevel
        }

    /** The spellbook as the warning names it: "Ancient", "regular". */
    fun bookName(spellbook: Spellbook): String =
        when (spellbook) {
            Spellbook.REGULAR -> "regular"
            Spellbook.ANCIENT -> "Ancient"
        }

    /** The charges after a jewellery's name: "(4)". */
    private val CHARGES = Regex("""\(\d+\)$""")

    /** The lit spell icons of the two spellbooks (`magicon` for 1151, `magicon2` for 12855), read from the 377 interface archive (2026-10-16). */
    private val PICTURES = mapOf(
        TeleportSpell.VARROCK to StepIcon.Media("magicon", 12),
        TeleportSpell.LUMBRIDGE to StepIcon.Media("magicon", 15),
        TeleportSpell.FALADOR to StepIcon.Media("magicon", 18),
        TeleportSpell.CAMELOT to StepIcon.Media("magicon", 22),
        TeleportSpell.ARDOUGNE to StepIcon.Media("magicon", 39),
        TeleportSpell.WATCHTOWER to StepIcon.Media("magicon", 40),
        TeleportSpell.TROLLHEIM to StepIcon.Media("magicon2", 4),
        TeleportSpell.APE_ATOLL to StepIcon.Media("magicon2", 38),
        TeleportSpell.PADDEWWA to StepIcon.Media("magicon2", 22),
        TeleportSpell.SENNTISTEN to StepIcon.Media("magicon2", 23),
        TeleportSpell.KHARYRLL to StepIcon.Media("magicon2", 24),
        TeleportSpell.LASSAR to StepIcon.Media("magicon2", 25),
        TeleportSpell.DAREEYAK to StepIcon.Media("magicon2", 26),
        TeleportSpell.CARRALLANGER to StepIcon.Media("magicon2", 27),
        TeleportSpell.ANNAKARL to StepIcon.Media("magicon2", 28),
        TeleportSpell.GHORROCK to StepIcon.Media("magicon2", 29),
    )
}
