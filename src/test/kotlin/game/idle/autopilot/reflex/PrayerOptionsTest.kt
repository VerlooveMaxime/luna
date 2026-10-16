package game.idle.autopilot.reflex

import engine.combat.prayer.CombatPrayer
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionFacts
import game.idle.flow.option.OptionIcon
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PrayerOptionsTest {

    private fun melee(facts: OptionFacts = OptionFacts()) = PrayerOptions.options(OptionContext(facts)).single { it.value == "5623" }

    @Test
    fun `every Luna prayer is offered once, by its tab button`() {
        assertEquals(CombatPrayer.entries.map { it.button.toString() }, PrayerOptions.options(OptionContext()).map { it.value })
    }

    @Test
    fun `a row shows the prayer's name, its lit tab icon and the level it needs`() {
        val row = melee(OptionFacts(levels = mapOf(Skill.PRAYER to 43)))

        assertEquals(listOf("Protect from melee", OptionIcon.Media("prayeron", 14), "Prayer 43", 43, null), listOf(row.label, row.icon, row.note, row.level, row.blocked))
    }

    @Test
    fun `a prayer above the base level is greyed with the level it needs`() {
        assertEquals("needs Prayer 43", melee(OptionFacts(levels = mapOf(Skill.PRAYER to 42))).blocked)
    }

    @Test
    fun `a boosted level never unlocks a prayer`() {
        val boosted = OptionFacts(levels = mapOf(Skill.PRAYER to 60), baseLevels = mapOf(Skill.PRAYER to 42))

        assertEquals("needs Prayer 43", melee(boosted).blocked)
    }

    @Test
    fun `low points never lock a prayer the base level unlocks`() {
        val drained = OptionFacts(levels = mapOf(Skill.PRAYER to 0), baseLevels = mapOf(Skill.PRAYER to 43))

        assertNull(melee(drained).blocked)
    }
}
