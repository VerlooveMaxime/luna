package game.idle.autopilot.fighting

import game.idle.flow.option.FakeNames
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionFacts
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.StepOption
import game.player.item.consume.food.Food
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FightOptionsTest {

    private val cows = FightTarget("cow", setOf(397, 81), "Cow", 2..2)
    private val rats = FightTarget("giant rat", setOf(86, 87), "Giant rat", 3..6)

    @Test
    fun `an npc of one level shows that level, with its lowest id's head`() {
        val rows = FightOptions(FightTargetCatalog(listOf(cows))).options(OptionContext())

        assertEquals(listOf(StepOption("cow", "Cow", OptionIcon.Npc(81), "level 2", level = 2)), rows)
    }

    @Test
    fun `npcs of several levels show the span`() {
        val rows = FightOptions(FightTargetCatalog(listOf(rats))).options(OptionContext())

        assertEquals("level 3-6", rows.single().note)
    }

    @Test
    fun `any food comes first`() {
        val rows = EatOptions(FakeNames()).options(OptionContext())

        assertEquals(StepOption("any", "Any food", OptionIcon.Item(Food.BREAD.id), "the first in the bag", group = -1), rows.first())
    }

    @Test
    fun `every food Luna knows follows, with the bank's count`() {
        val context = OptionContext(OptionFacts(bank = mapOf(Food.SHRIMP.id to 60)))

        val shrimps = EatOptions(FakeNames(items = mapOf(Food.SHRIMP.id to "Shrimps"))).options(context).single { it.label == "Shrimps" }

        assertEquals(listOf("315", "60 in bank", "60"), listOf(shrimps.value, shrimps.note, shrimps.banked.toString()))
    }

    @Test
    fun `every food Luna knows is a row`() {
        assertEquals(Food.entries.size + 1, EatOptions(FakeNames()).options(OptionContext()).size)
    }
}
