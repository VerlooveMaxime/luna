package game.idle.flow

import engine.combat.prayer.CombatPrayer

/**
 * Luna's prayers as a reflex keeps, names and pictures them (S13 part 3, Maxime 2026-10-16): kept by the button the
 * prayer tab clicks them with, a stable fact of the cache; named in sentence case, "Protect from melee", in the list,
 * the row and the chat alike; pictured with the tab's lit icon.
 */
object Prayers {

    /** The prayer whose tab button is [key], null for none. */
    fun of(key: Int): CombatPrayer? = CombatPrayer.entries.firstOrNull { it.button == key }

    fun key(prayer: CombatPrayer): Int = prayer.button

    /** "Protect from melee", "Thick skin": the enum's name in sentence case. */
    fun named(prayer: CombatPrayer): String = prayer.name.substring(0, 1) + prayer.name.substring(1).lowercase().replace('_', ' ')

    fun picture(prayer: CombatPrayer): StepIcon = StepIcon.Media(LIT, PICTURES.getValue(prayer))

    /** The first two of [prayers] that cannot be on together (one of Luna's groups), in list order, or null. */
    fun clash(prayers: List<CombatPrayer>): Pair<CombatPrayer, CombatPrayer>? =
        prayers.withIndex().firstNotNullOfOrNull { (index, prayer) ->
            prayers.drop(index + 1).firstOrNull { it.group != null && it.group == prayer.group }?.let { prayer to it }
        }

    /** The sprite strip the prayer tab draws a lit prayer with; `prayeroff` is the dim one. */
    const val LIT = "prayeron"

    /**
     * Each prayer's index in the tab's `prayeron` and `prayeroff` strips, read from the 377 interface archive
     * (2026-10-16): the tab's order, but Redemption's picture comes before Retribution's.
     */
    private val PICTURES = mapOf(
        CombatPrayer.THICK_SKIN to 0,
        CombatPrayer.BURST_OF_STRENGTH to 1,
        CombatPrayer.CLARITY_OF_THOUGHT to 2,
        CombatPrayer.ROCK_SKIN to 3,
        CombatPrayer.SUPERHUMAN_STRENGTH to 4,
        CombatPrayer.IMPROVED_REFLEXES to 5,
        CombatPrayer.RAPID_RESTORE to 6,
        CombatPrayer.RAPID_HEAL to 7,
        CombatPrayer.PROTECT_ITEM to 8,
        CombatPrayer.STEEL_SKIN to 9,
        CombatPrayer.ULTIMATE_STRENGTH to 10,
        CombatPrayer.INCREDIBLE_REFLEXES to 11,
        CombatPrayer.PROTECT_FROM_MAGIC to 12,
        CombatPrayer.PROTECT_FROM_MISSILES to 13,
        CombatPrayer.PROTECT_FROM_MELEE to 14,
        CombatPrayer.REDEMPTION to 15,
        CombatPrayer.RETRIBUTION to 16,
        CombatPrayer.SMITE to 17,
    )
}
