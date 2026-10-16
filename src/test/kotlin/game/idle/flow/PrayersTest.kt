package game.idle.flow

import engine.combat.prayer.CombatPrayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PrayersTest {

    @Test
    fun `a prayer is kept by its tab button and found by it`() {
        assertEquals(5623, Prayers.key(CombatPrayer.PROTECT_FROM_MELEE))
        assertEquals(CombatPrayer.PROTECT_FROM_MELEE, Prayers.of(5623))
    }

    @Test
    fun `a key that is no button is no prayer`() {
        assertNull(Prayers.of(1511))
    }

    @Test
    fun `a prayer is named in sentence case`() {
        assertEquals(listOf("Protect from melee", "Thick skin", "Protect item"), listOf(CombatPrayer.PROTECT_FROM_MELEE, CombatPrayer.THICK_SKIN, CombatPrayer.PROTECT_ITEM).map(Prayers::named))
    }

    @Test
    fun `a prayer's picture is its lit icon on the tab, Redemption's before Retribution's`() {
        assertEquals(
            listOf(StepIcon.Media("prayeron", 0), StepIcon.Media("prayeron", 14), StepIcon.Media("prayeron", 15), StepIcon.Media("prayeron", 16), StepIcon.Media("prayeron", 17)),
            listOf(CombatPrayer.THICK_SKIN, CombatPrayer.PROTECT_FROM_MELEE, CombatPrayer.REDEMPTION, CombatPrayer.RETRIBUTION, CombatPrayer.SMITE).map(Prayers::picture),
        )
    }

    @Test
    fun `every prayer has a picture of its own`() {
        assertEquals(CombatPrayer.entries.size, CombatPrayer.entries.map(Prayers::picture).toSet().size)
    }

    @Test
    fun `two prayers of one group clash, the first pair in list order`() {
        assertEquals(
            CombatPrayer.THICK_SKIN to CombatPrayer.ROCK_SKIN,
            Prayers.clash(listOf(CombatPrayer.THICK_SKIN, CombatPrayer.BURST_OF_STRENGTH, CombatPrayer.ROCK_SKIN, CombatPrayer.STEEL_SKIN)),
        )
    }

    @Test
    fun `prayers of different groups do not clash`() {
        assertNull(Prayers.clash(listOf(CombatPrayer.THICK_SKIN, CombatPrayer.BURST_OF_STRENGTH, CombatPrayer.PROTECT_FROM_MELEE)))
    }

    @Test
    fun `prayers without a group never clash`() {
        assertNull(Prayers.clash(listOf(CombatPrayer.RAPID_RESTORE, CombatPrayer.RAPID_HEAL, CombatPrayer.PROTECT_ITEM)))
    }
}
