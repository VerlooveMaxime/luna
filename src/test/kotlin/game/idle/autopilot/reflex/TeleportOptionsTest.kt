package game.idle.autopilot.reflex

import game.idle.flow.option.FakeNames
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionFacts
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.StepOption
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TeleportOptionsTest {

    private val glory = 1712
    private val options = TeleportOptions(FakeNames(mapOf(glory to "Amulet of glory(4)", 2552 to "Ring of dueling(8)", 3853 to "Games necklace(8)", 1963 to "Banana")))

    private fun of(facts: OptionFacts = OptionFacts()): List<StepOption> = options.options(OptionContext(facts))

    @Test
    fun `every teleport is offered, spells then jewellery`() {
        assertEquals(19, of().size)
        assertEquals(listOf("varrock", "games_necklace:burthorpe_games_rooms"), listOf(of().first().value, of()[12].value))
    }

    @Test
    fun `a spell shows its icon, runes and level, greyed below the base Magic level`() {
        val varrock = of(OptionFacts(levels = mapOf(Skill.MAGIC to 30), baseLevels = mapOf(Skill.MAGIC to 20))).first()

        assertEquals(StepOption("varrock", "Varrock", OptionIcon.Media("magicon", 12), "3 air, 1 fire, 1 law", blocked = "needs Magic 25", level = 25), varrock)
    }

    @Test
    fun `a spell at the base level is not greyed`() {
        assertEquals(null, of(OptionFacts(levels = mapOf(Skill.MAGIC to 25))).first().blocked)
    }

    @Test
    fun `a jewellery destination shows the jewellery, counts its charged pieces in the bank and comes after the spells`() {
        val edgeville = of(OptionFacts(bank = mapOf(glory to 1, 1706 to 2))).first { it.value == "amulet_of_glory:edgeville" }

        assertEquals(StepOption("amulet_of_glory:edgeville", "Edgeville (amulet of glory)", OptionIcon.Item(glory), "3 in bank", banked = 3, group = 1), edgeville)
    }

    @Test
    fun `a jewellery the bank has none of says so`() {
        assertEquals("none in bank", of().first { it.value == "amulet_of_glory:edgeville" }.note)
    }
}
